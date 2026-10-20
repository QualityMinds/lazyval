package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.qualityminds.lazyval.naming.DotName;
import com.qualityminds.lazyval.processor.spi.PayloadExpr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders one of the SPI's expressions as a JavaPoet {@link CodeBlock}, so the type names it mentions
 * arrive as imports rather than as fully qualified text.
 *
 * <p>Lives here rather than on {@link PayloadExpr} because the SPI deliberately knows nothing about
 * JavaPoet: it describes an expression, and whoever writes the code decides how to render it. Stock
 * generators all render it the same way, so they share this.
 */
public final class JavaPoetExprs {

    private static final Pattern SLOT = Pattern.compile("\\$[TL]");

    private JavaPoetExprs() {
    }

    /**
     * Renders an expression as a code block, so that every type it mentions becomes an import.
     *
     * @param expr an expression from {@link com.qualityminds.lazyval.processor.spi.JavaPayload}
     * @return the same expression, with each type handed to JavaPoet as a {@code $T} argument
     */
    public static CodeBlock code(PayloadExpr expr) {
        var formatted = expr.asFormat("$T");
        Object[] args = formatted.types().stream().map(JavaPoetExprs::className).toArray();
        return CodeBlock.of(formatted.format(), args);
    }

    /**
     * Renders an expression that was built around {@code placeholder}, with {@code substitute} in its
     * place. Unlike handing the substitute to the SPI as text, every type it mentions still becomes an
     * import.
     *
     * <pre>{@code
     * code(element.java().create("payload"), "payload", CodeBlock.of("convert(source, $T.class)", type))
     * }</pre>
     *
     * @param expr an expression from {@link com.qualityminds.lazyval.processor.spi.JavaPayload}
     * @param placeholder the text {@code expr} was built with; must not contain {@code $}
     * @param substitute the code block to render in its place
     * @return the expression, with the placeholder replaced and each type handed to JavaPoet
     */
    public static CodeBlock code(PayloadExpr expr, String placeholder, CodeBlock substitute) {
        var formatted = expr.asFormat("$T");
        String format = formatted.format().replace(placeholder, "$L");
        // the substitute's slot sits somewhere between the expression's type slots, so the arguments
        // are collected in the order the slots appear
        List<Object> args = new ArrayList<>();
        Iterator<DotName> types = formatted.types().iterator();
        Matcher slots = SLOT.matcher(format);
        while (slots.find()) {
            args.add("$T".equals(slots.group()) ? className(types.next()) : substitute);
        }
        return CodeBlock.of(format, args.toArray());
    }

    /** JavaPoet wants the package and each simple name separately, which a DotName already carries. */
    private static ClassName className(DotName name) {
        var simpleNames = name.simpleNames();
        return ClassName.get(name.packageName(), simpleNames.get(0),
                simpleNames.subList(1, simpleNames.size()).toArray(String[]::new));
    }
}
