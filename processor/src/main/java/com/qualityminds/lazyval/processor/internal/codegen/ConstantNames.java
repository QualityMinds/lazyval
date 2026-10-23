package com.qualityminds.lazyval.processor.internal.codegen;

import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Names for generated holder classes exposing one constant per domain-primitive, such as Spring Data's
 * {@code RedisSerializers} or {@code LazyvalLettuceCodecs}.
 */
public final class ConstantNames {

    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    private ConstantNames() {
    }

    /**
     * {@code IdsProductId} → {@code IDS_PRODUCT_ID}, {@code EMail} → {@code E_MAIL}.
     *
     * @param element the domain-primitive
     * @return the constant name, derived from the flat name of the type
     */
    public static String of(ValidatedGeneratorElement element) {
        return WORD_BOUNDARY.matcher(element.name().flatName()).replaceAll("_").toUpperCase(Locale.ROOT);
    }

    /**
     * Reports every constant two or more types would share: a holder with duplicate fields does not compile.
     *
     * @param elements  the domain-primitives getting a constant
     * @param context   where errors are reported
     * @param generator the generator reporting them
     * @return {@code true} if every constant name is unique
     */
    public static boolean unique(Iterable<ValidatedGeneratorElement> elements, Generator.Context context,
                                 Generator generator) {
        Map<String, List<String>> byConstant = new TreeMap<>();
        for (ValidatedGeneratorElement element : elements) {
            byConstant.computeIfAbsent(of(element), k -> new ArrayList<>()).add(element.name().canonicalName());
        }
        boolean unique = true;
        for (var entry : byConstant.entrySet()) {
            if (entry.getValue().size() > 1) {
                context.logError(generator, "types " + entry.getValue() + " would share the constant '"
                        + entry.getKey() + "' in the generated code; rename one of them");
                unique = false;
            }
        }
        return unique;
    }
}
