package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.qualityminds.lazyval.processor.internal.LazyvalElementValidatorMessages.*;

/**
 * Judges a {@link TypeElement} by Lazyval's rules, reports every rule it breaks via the supplied
 * {@link LazyvalEnvironment}, and describes a type that breaks none as a {@link ValidatedGeneratorElement} for the
 * generators.
 * <p>
 * {@link #validate} is the list of rules, in the order they are judged, and each block in it starts with the
 * section of {@code rules.adoc} that explains the rule to users. What a rule judges is looked up beforehand, by
 * convention, and handed to it as data: {@link PayloadLookup} says which member holds the payload,
 * {@link CreatorLookup} what rebuilds the value from it. The wording of every diagnostic is
 * {@link LazyvalElementValidatorMessages}'. The KSP validator is laid out the same way.
 */
class LazyvalElementValidator {

    private final Types typeUtils;
    private final LazyvalEnvironment environment;

    LazyvalElementValidator(Types typeUtils, LazyvalEnvironment environment) {
        this.typeUtils = Objects.requireNonNull(typeUtils);
        this.environment = Objects.requireNonNull(environment);
    }

    /**
     * Every rule is judged before the type is rejected, so a type that breaks several hears about each of them in
     * one compiler run instead of in one fix-and-recompile cycle apiece. Only a missing subject ends the judgement
     * early: a type that is not a record or a class, or one without exactly one payload, leaves the rules after it
     * nothing to judge.
     */
    Optional<ValidatedGeneratorElement> validate(TypeElement type) {
        // Shape: a record or a class. An interface, an enum or an annotation type has no instance to rebuild.
        if (type.getKind() != ElementKind.RECORD && type.getKind() != ElementKind.CLASS) {
            return reject(List.of(new Finding(type, UNSUPPORTED_KIND_MESSAGE)));
        }
        var errors = new ArrayList<Finding>();
        // Visibility: generated code is emitted into another package and has to name the type there.
        if (!isPublic(type)) {
            errors.add(new Finding(type, nonPublicTypeMessage(type)));
        }
        // Shape: an abstract class has nothing to instantiate.
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            errors.add(new Finding(type, ABSTRACT_CLASS_MESSAGE));
        }

        // Exactly one payload, read through a public accessor. Without it, nothing is left to judge.
        // With Java 21 as the baseline, this chain becomes a pattern-matching switch over the sealed PayloadLookup,
        // and the compiler checks that no case is left out.
        var lookup = PayloadLookup.of(type);
        if (lookup instanceof PayloadLookup.None none) {
            errors.add(new Finding(type, noPayloadMessage(type, none.transientOnly())));
        } else if (lookup instanceof PayloadLookup.Unreadable unreadable) {
            // Visibility: each field nobody can read, reported where the author adds or widens its accessor.
            for (var unreadableField : unreadable.fields()) {
                var field = unreadableField.field();
                var accessor = unreadableField.nonPublicAccessor();
                errors.add(accessor == null
                        ? new Finding(field, missingAccessorMessage(field))
                        : new Finding(accessor, nonPublicAccessorMessage(field, accessor)));
            }
        } else if (lookup instanceof PayloadLookup.Several) {
            errors.add(new Finding(type, severalPayloadsMessage(type)));
        }
        if (!(lookup instanceof PayloadLookup.Found payload)) {
            return reject(errors);
        }
        // Payload type: not an array, which stays mutable behind any accessor and compares by identity.
        if (payload.type().getKind() == TypeKind.ARRAY) {
            errors.add(new Finding(payload.declaration(), arrayPayloadMessage(payload.declaration())));
        }

        // Factory methods: the value is rebuilt through the factory method where there is exactly one, through the
        // constructor taking the payload otherwise — public either way, as generated code calls it from another
        // package. With Java 21 as the baseline, this chain becomes a pattern-matching switch over the sealed
        // CreatorLookup, its visibility conditions turning into guards.
        var creator = CreatorLookup.of(type, payload.type(), typeUtils);
        if (creator instanceof CreatorLookup.SeveralFactories several) {
            errors.add(new Finding(type, severalFactoriesMessage(several.factories())));
        } else if (creator instanceof CreatorLookup.Factory factory && !isPublic(factory.declaration())) {
            errors.add(new Finding(factory.declaration(), nonPublicFactoryMessage(factory.declaration())));
        } else if (creator instanceof CreatorLookup.Constructor constructor
                // A record's canonical constructor merely inherits the type's visibility, which is reported above.
                && isPublic(type) && !isPublic(constructor.declaration())) {
            errors.add(new Finding(constructor.declaration(),
                    nonPublicConstructorMessage(type, constructor.declaration())));
        } else if (creator instanceof CreatorLookup.Missing) {
            errors.add(new Finding(type,
                    missingReconstructionMessage(type, payload.type(), payload.transientComponents())));
        }
        if (!(creator instanceof CreatorLookup.Found found) || !errors.isEmpty()) {
            return reject(errors);
        }

        // Immutability: advice rather than rules, given only to a type every rule accepted. A record is final, and
        // so is its component.
        if (type.getKind() == ElementKind.CLASS && !type.getModifiers().contains(Modifier.FINAL)) {
            environment.warn(type, NOT_FINAL_CLASS_WARNING);
        }
        if (payload.declaration().getKind() == ElementKind.FIELD
                && !payload.declaration().getModifiers().contains(Modifier.FINAL)) {
            environment.warn(payload.declaration(), NOT_FINAL_PAYLOAD_WARNING);
        }
        // With Java 21 as the baseline, a switch expression over the sealed CreatorLookup.Found.
        var factoryMethod = found instanceof CreatorLookup.Factory factory ? factory.declaration() : null;
        return Optional.of(ValidatedGeneratorElement.of(type, payload.type(), payload.accessor(), factoryMethod));
    }

    /** Reports each of {@code errors}, and nothing is generated for the type. */
    private Optional<ValidatedGeneratorElement> reject(List<Finding> errors) {
        errors.forEach(error -> environment.error(error.element(), error.message()));
        return Optional.empty();
    }

    private static boolean isPublic(Element element) {
        return element.getModifiers().contains(Modifier.PUBLIC);
    }

    /** A broken rule: what is wrong, and the declaration the author has to change. */
    private record Finding(Element element, String message) {}
}
