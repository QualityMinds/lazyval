package com.qualityminds.lazyval.naming;

import java.util.List;
import java.util.Objects;

/**
 * The name of a declaration, kept in the one form that cannot be misread: its package and its simple
 * names, already separated.
 *
 * <p>A generator needs the same name in four different spellings, and picking the wrong one produces
 * source that does not compile. For a nested {@code ProductId} declared inside {@code Ids}:
 *
 * <pre>{@code
 * DotName name = DotName.of("com.acme.order", "Ids", "ProductId");
 *
 * name.canonicalName();  // "com.acme.order.Ids.ProductId" - naming the type in full
 * name.nestedName();     // "Ids.ProductId"                - naming it once it is imported
 * name.simpleName();     // "ProductId"                    - the declaration's own name
 * name.flatName();       // "IdsProductId"                 - building a new identifier from it
 * }</pre>
 *
 * <p>{@link #flatName()} is the one that is easy to get wrong. A generated class or file name must not
 * contain a dot, so a generator deriving {@code ProductIdCodec} from a nested type has to flatten the
 * enclosing names into it — otherwise two nested types called {@code ProductId} in different enclosing
 * classes generate the same file, and the second silently overwrites the first.
 *
 * <h2>Why there is no {@code parse(String)}</h2>
 *
 * <p>Splitting {@code "com.acme.order.Ids.ProductId"} back into a package and two simple names cannot
 * be done from the string alone; JavaPoet's {@code ClassName.bestGuess} does it by assuming the first
 * capitalised segment starts the type, which is a convention rather than a rule. Lazyval always learns
 * the split from the compiler — {@code KSClassDeclaration.packageName} plus its enclosing declarations,
 * or a {@code TypeElement} plus its enclosing elements — so a {@code DotName} is built once, where the
 * answer is authoritative, and passed on. Everything downstream reads a spelling off it instead of
 * guessing at a string.
 *
 * <p>By the same reasoning this describes a <em>declaration's name</em> and nothing else: no type
 * arguments, no nullability, no array or primitive forms. Those belong to a type, they differ between
 * the two languages Lazyval generates, and JavaPoet and KotlinPoet already model them well. Both accept
 * the parts of a {@code DotName} directly — {@code ClassName.get(pkg, first, rest...)} and
 * {@code ClassName(pkg, first, rest...)} — which is the whole handover.
 *
 * @param packageName the package, or the empty string for the default package
 * @param simpleNames the simple names from the outermost enclosing declaration inwards; never empty,
 *                    and never containing a dot
 */
public record DotName(String packageName, List<String> simpleNames) {

    /**
     * Canonical constructor, rejecting anything that would make the spellings below ambiguous.
     *
     * @throws NullPointerException if any argument or element is {@code null}
     * @throws IllegalArgumentException if there are no simple names, or one is blank or contains a dot
     */
    public DotName {
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(simpleNames, "simpleNames");
        if (simpleNames.isEmpty()) {
            throw new IllegalArgumentException("A DotName needs at least one simple name");
        }
        simpleNames.forEach(simple -> {
            Objects.requireNonNull(simple, "simpleName");
            if (simple.isBlank()) {
                throw new IllegalArgumentException("A simple name must not be blank");
            }
            if (simple.indexOf('.') >= 0) {
                throw new IllegalArgumentException(
                        "A simple name must not contain a dot, but was '" + simple
                                + "'. Pass each enclosing name separately, so that nesting stays known "
                                + "rather than having to be guessed back out of the string.");
            }
        });
        simpleNames = List.copyOf(simpleNames);
    }

    /**
     * Creates a name from its parts, outermost enclosing declaration first.
     *
     * @param packageName the package, or the empty string for the default package
     * @param simpleNames the simple names, outermost first; at least one, none containing a dot
     * @return the name
     */
    public static DotName of(String packageName, String... simpleNames) {
        return new DotName(packageName, List.of(simpleNames));
    }

    /**
     * The declaration's own simple name, without any enclosing one.
     * @return {@code "ProductId"}
     */
    public String simpleName() {
        return simpleNames.get(simpleNames.size() - 1);
    }

    /**
     * The simple names joined by dots, which is how source refers to the type once it is imported.
     * @return {@code "Ids.ProductId"}
     */
    public String nestedName() {
        return String.join(".", simpleNames);
    }

    /**
     * The fully qualified name, which is how source refers to the type without an import.
     * @return {@code "com.acme.order.Ids.ProductId"}
     */
    public String canonicalName() {
        return packageName.isEmpty() ? nestedName() : packageName + "." + nestedName();
    }

    /**
     * The simple names concatenated, safe to use as an identifier and unique among the declarations of
     * a package. Use this to derive the name of something generated <em>from</em> this declaration.
     * @return {@code "IdsProductId"}
     */
    public String flatName() {
        return String.join("", simpleNames);
    }

    /** @return {@link #canonicalName()} */
    @Override
    public String toString() {
        return canonicalName();
    }
}
