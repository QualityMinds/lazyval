package com.qualityminds.lazyval.processor.internal.codegen.springdata;

import com.google.errorprone.annotations.Immutable;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;

import java.util.List;

/**
 * The Spring Data stores this generator can register converters with.
 * <p>
 * Everything that differs between stores lives here, so adding one is a single constant rather than
 * edits scattered across classpath detection, option handling, and code emission.
 * <p>
 * <b>Declaration order is emission order</b> — the bean methods appear in the generated configuration
 * in the order below. Reordering churns the generated sources of every consumer, so don't.
 */
enum SpringDataStore {

    CASSANDRA(
            "org.springframework.data.cassandra.core.convert.CassandraCustomConversions",
            ClassName.get("org.springframework.data.cassandra.core.convert", "CassandraCustomConversions"),
            "lazyval.springdata.cassandra.converters",
            "Cassandra",
            "cassandraCustomConversions",
            Construction.plain()),

    MONGO(
            "org.springframework.data.mongodb.core.convert.MongoCustomConversions",
            ClassName.get("org.springframework.data.mongodb.core.convert", "MongoCustomConversions"),
            "lazyval.springdata.mongo.converters",
            "MongoDB",
            "mongoCustomConversions",
            Construction.plain()),

    JDBC(
            "org.springframework.data.jdbc.core.convert.JdbcCustomConversions",
            ClassName.get("org.springframework.data.jdbc.core.convert", "JdbcCustomConversions"),
            "lazyval.springdata.jdbc.converters",
            "JDBC",
            "jdbcCustomConversions",
            // Spring Boot publishes a JdbcDialect bean, so it can simply be injected
            Construction.viaDialect(
                    ClassName.get("org.springframework.data.jdbc.core.dialect", "JdbcDialect"),
                    "dialect",
                    CodeBlock.of("dialect"))),

    R2DBC(
            "org.springframework.data.r2dbc.convert.R2dbcCustomConversions",
            ClassName.get("org.springframework.data.r2dbc.convert", "R2dbcCustomConversions"),
            "lazyval.springdata.r2dbc.converters",
            "R2DBC",
            "r2dbcCustomConversions",
            // Spring Boot publishes no R2dbcDialect bean — DataR2dbcAutoConfiguration resolves it in
            // its own constructor and keeps it private — so it is resolved from the ConnectionFactory
            // bean. Taking an R2dbcDialect parameter compiles, but leaves the bean unsatisfiable at
            // runtime.
            Construction.viaDialect(
                    ClassName.get("io.r2dbc.spi", "ConnectionFactory"),
                    "connectionFactory",
                    CodeBlock.of("$T.getDialect(connectionFactory)",
                            ClassName.get("org.springframework.data.r2dbc.dialect", "DialectResolver"))));

    private final String conversionsFqn;
    private final ClassName conversionsType;
    private final String optionKey;
    private final String label;
    private final String beanName;
    private final Construction construction;

    SpringDataStore(String conversionsFqn, ClassName conversionsType, String optionKey,
                    String label, String beanName, Construction construction) {
        this.conversionsFqn = conversionsFqn;
        this.conversionsType = conversionsType;
        this.optionKey = optionKey;
        this.label = label;
        this.beanName = beanName;
        this.construction = construction;
    }

    /** Probed on the compile classpath to decide whether this store is in play. */
    String conversionsFqn() {
        return conversionsFqn;
    }

    /** The {@code CustomConversions} type the bean method returns. */
    ClassName conversionsType() {
        return conversionsType;
    }

    /** Option feeding user-supplied converters to this store. */
    String optionKey() {
        return optionKey;
    }

    /** How the store is named in warnings addressed to the user. */
    String label() {
        return label;
    }

    /** Name of the generated bean method. */
    String beanName() {
        return beanName;
    }

    Construction construction() {
        return construction;
    }

    /**
     * How a store's {@code CustomConversions} instance is constructed. The two shapes differ
     * structurally — one takes a bean-method parameter and one does not — so each owns its own
     * emission instead of the caller branching on a nullable field.
     * <p>
     * Behaviour lives on the subtypes rather than in a {@code switch}: pattern matching for switch is
     * only standard from Java 21, and this project targets 17, so a switch here could not be checked
     * for exhaustiveness. With abstract methods a new shape cannot compile until it is implemented.
     * <p>
     * {@code @Immutable} is what lets the enum hold one of these as a constant field. It is not
     * decoration: it makes error-prone verify that every implementation stays immutable.
     */
    @Immutable
    sealed interface Construction {

        /** Parameters the generated bean method must declare. */
        List<ParameterSpec> parameters();

        /** Adds the {@code return} statement building the {@code CustomConversions} from converters. */
        void addReturnStatement(MethodSpec.Builder builder, ClassName conversionsType);

        static Construction plain() {
            return new Plain();
        }

        static Construction viaDialect(ClassName parameterType, String parameterName,
                                      CodeBlock dialectExpression) {
            return new ViaDialect(parameterType, parameterName, dialectExpression);
        }

        /** Cassandra and MongoDB: a plain collection constructor. */
        record Plain() implements Construction {
            @Override
            public List<ParameterSpec> parameters() {
                return List.of();
            }

            @Override
            public void addReturnStatement(MethodSpec.Builder builder, ClassName conversionsType) {
                builder.addStatement("return new $T(converters)", conversionsType);
            }
        }

        /**
         * The relational stores, whose conversions are dialect-dependent: the dialect contributes its
         * own simple types and converters, and neither {@code JdbcCustomConversions} nor
         * {@code R2dbcCustomConversions} picks those up from a plain collection constructor. Built
         * with {@code of(dialect, converters)}, exactly as Spring Data's own configuration does.
         */
        record ViaDialect(ClassName parameterType, String parameterName, CodeBlock dialectExpression)
                implements Construction {
            @Override
            public List<ParameterSpec> parameters() {
                return List.of(ParameterSpec.builder(parameterType, parameterName).build());
            }

            @Override
            public void addReturnStatement(MethodSpec.Builder builder, ClassName conversionsType) {
                builder.addStatement("return $T.of($L, converters)", conversionsType, dialectExpression);
            }
        }
    }
}
