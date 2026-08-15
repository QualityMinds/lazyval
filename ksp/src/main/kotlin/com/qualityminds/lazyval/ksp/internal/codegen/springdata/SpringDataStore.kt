package com.qualityminds.lazyval.ksp.internal.codegen.springdata

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec

/**
 * The Spring Data stores this generator can register converters with.
 *
 * Everything that differs between stores lives here, so adding one is a single constant rather than
 * edits scattered across classpath detection, option handling and code emission.
 *
 * **Declaration order is emission order** — the bean methods appear in the generated configuration in
 * the order below. Reordering churns the generated sources of every consumer, so don't.
 */
internal enum class SpringDataStore(
    /** probed on the compile classpath to decide whether this store is in play */
    val conversionsFqn: String,
    /** the `CustomConversions` type the bean method returns */
    val conversionsType: ClassName,
    /** option feeding user-supplied converters to this store */
    val optionKey: String,
    /** how the store is named in warnings addressed to the user */
    val label: String,
    /** name of the generated bean method */
    val beanName: String,
    /** how the `CustomConversions` instance is built — see [Construction] */
    val construction: Construction,
) {

    CASSANDRA(
        conversionsFqn = "org.springframework.data.cassandra.core.convert.CassandraCustomConversions",
        conversionsType = ClassName("org.springframework.data.cassandra.core.convert", "CassandraCustomConversions"),
        optionKey = "lazyval.springdata.cassandra.converters",
        label = "Cassandra",
        beanName = "cassandraCustomConversions",
        construction = Construction.Plain,
    ),

    MONGO(
        conversionsFqn = "org.springframework.data.mongodb.core.convert.MongoCustomConversions",
        conversionsType = ClassName("org.springframework.data.mongodb.core.convert", "MongoCustomConversions"),
        optionKey = "lazyval.springdata.mongo.converters",
        label = "MongoDB",
        beanName = "mongoCustomConversions",
        construction = Construction.Plain,
    ),

    JDBC(
        conversionsFqn = "org.springframework.data.jdbc.core.convert.JdbcCustomConversions",
        conversionsType = ClassName("org.springframework.data.jdbc.core.convert", "JdbcCustomConversions"),
        optionKey = "lazyval.springdata.jdbc.converters",
        label = "JDBC",
        beanName = "jdbcCustomConversions",
        // Spring Boot publishes a JdbcDialect bean, so it can simply be injected
        construction = Construction.ViaDialect(
            parameterType = ClassName("org.springframework.data.jdbc.core.dialect", "JdbcDialect"),
            parameterName = "dialect",
            dialectExpression = com.squareup.kotlinpoet.CodeBlock.of("dialect"),
        ),
    ),

    R2DBC(
        conversionsFqn = "org.springframework.data.r2dbc.convert.R2dbcCustomConversions",
        conversionsType = ClassName("org.springframework.data.r2dbc.convert", "R2dbcCustomConversions"),
        optionKey = "lazyval.springdata.r2dbc.converters",
        label = "R2DBC",
        beanName = "r2dbcCustomConversions",
        // Spring Boot publishes no R2dbcDialect bean — DataR2dbcAutoConfiguration resolves it in its
        // own constructor and keeps it private — so it is resolved from the ConnectionFactory bean.
        // Taking an R2dbcDialect parameter compiles, but leaves the bean unsatisfiable at runtime.
        construction = Construction.ViaDialect(
            parameterType = ClassName("io.r2dbc.spi", "ConnectionFactory"),
            parameterName = "connectionFactory",
            dialectExpression = com.squareup.kotlinpoet.CodeBlock.of(
                "%T.getDialect(connectionFactory)",
                ClassName("org.springframework.data.r2dbc.dialect", "DialectResolver"),
            ),
        ),
    ),
    ;

    /**
     * How a store's `CustomConversions` instance is constructed. The two shapes differ structurally —
     * one takes a bean-method parameter and one does not — so each owns its own emission instead of
     * the caller branching on a nullable field.
     */
    internal sealed interface Construction {

        /** Parameters the generated bean method must declare. */
        fun parameters(): List<ParameterSpec>

        /** Appends the `return` statement building the `CustomConversions` from `converters`. */
        fun addReturnStatement(builder: FunSpec.Builder, conversionsType: ClassName)

        /** Cassandra and MongoDB: a plain collection constructor. */
        data object Plain : Construction {
            override fun parameters(): List<ParameterSpec> = emptyList()

            override fun addReturnStatement(builder: FunSpec.Builder, conversionsType: ClassName) {
                builder.addStatement("return %T(converters)", conversionsType)
            }
        }

        /**
         * The relational stores, whose conversions are dialect-dependent: the dialect contributes its
         * own simple types and converters, and neither `JdbcCustomConversions` nor
         * `R2dbcCustomConversions` picks those up from a plain collection constructor. Built with
         * `of(dialect, converters)`, exactly as Spring Data's own configuration does.
         */
        data class ViaDialect(
            val parameterType: ClassName,
            val parameterName: String,
            val dialectExpression: com.squareup.kotlinpoet.CodeBlock,
        ) : Construction {
            override fun parameters(): List<ParameterSpec> =
                listOf(ParameterSpec.builder(parameterName, parameterType).build())

            override fun addReturnStatement(builder: FunSpec.Builder, conversionsType: ClassName) {
                builder.addStatement("return %T.of(%L, converters)", conversionsType, dialectExpression)
            }
        }
    }
}
