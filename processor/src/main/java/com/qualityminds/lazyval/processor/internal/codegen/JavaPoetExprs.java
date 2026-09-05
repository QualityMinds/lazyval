package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.qualityminds.lazyval.naming.DotName;
import com.qualityminds.lazyval.processor.spi.PayloadExpr;

/**
 * Renders one of the SPI's expressions as a JavaPoet {@link CodeBlock}, so the type names it mentions
 * arrive as imports rather than as fully qualified text.
 *
 * <p>Lives here rather than on {@link PayloadExpr} because the SPI deliberately knows nothing about
 * JavaPoet: it describes an expression, and whoever writes the code decides how to render it. Stock
 * generators all render it the same way, so they share this.
 */
public final class JavaPoetExprs {

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

    /** JavaPoet wants the package and each simple name separately, which a DotName already carries. */
    private static ClassName className(DotName name) {
        var simpleNames = name.simpleNames();
        return ClassName.get(name.packageName(), simpleNames.get(0),
                simpleNames.subList(1, simpleNames.size()).toArray(String[]::new));
    }
}
