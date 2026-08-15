package com.qualityminds.lazyval.processor

import com.qualityminds.lazyval.testkit.dependencies.Dependency

/**
 * The Spring Data stores the generator supports, and the handful of things that differ between them.
 *
 * Shared by {@code ApSpringDataIT} (generator behaviour) and {@code ApSpringDataConverterRulesIT}
 * (user-converter rules) so a new store is declared exactly once.
 */
class APSpringDataStores {

    /**
     * {@code toString} returns the label the generator uses in its "option ignored" warning, which is
     * also what appears in unrolled test names.
     */
    static class Store {
        /** as it appears in generator warnings and in test names */
        String label
        /** option feeding user-supplied converters to this store */
        String optionKey
        /** bean method the converters end up in */
        String beanMethod
        /** directory under src/test/resources/approvals holding the approved output */
        String approvalDir
        /** the store module, plus anything MavenResolver will not pull in transitively */
        List<Dependency> modules
        /**
         * Set only for the two stores that have a native codec generator the Spring Data generator
         * supersedes: the file that reappears once {@code lazyval.generators.supersede=false}.
         */
        String supersededCodec

        @Override
        String toString() { label }
    }

    static final Store CASSANDRA = new Store(
            label: 'Cassandra',
            optionKey: 'lazyval.springdata.cassandra.converters',
            beanMethod: 'cassandraCustomConversions',
            approvalDir: 'cassandra',
            modules: [springData('spring-data-cassandra', '4.4.6'),
                      // pulls the codec generator in, so supersede is exercised
                      new Dependency('com.datastax.oss', 'java-driver-core', '4.17.0')],
            supersededCodec: 'test/boundary/persistence/cassandra/LazyvalCassandraCodecs')

    static final Store MONGO = new Store(
            label: 'MongoDB',
            optionKey: 'lazyval.springdata.mongo.converters',
            beanMethod: 'mongoCustomConversions',
            approvalDir: 'mongo',
            modules: [springData('spring-data-mongodb', '4.4.6'),
                      // pulls the codec generator in, so supersede is exercised
                      new Dependency('org.mongodb', 'bson', '5.6.5')],
            supersededCodec: 'test/boundary/persistence/mongodb/LazyvalMongoCodecs')

    static final Store JDBC = new Store(
            label: 'JDBC',
            optionKey: 'lazyval.springdata.jdbc.converters',
            beanMethod: 'jdbcCustomConversions',
            approvalDir: 'jdbc',
            // JdbcCustomConversions.of takes the relational Dialect supertype of JdbcDialect, and
            // MavenResolver does not resolve transitively, so the module is listed explicitly
            modules: [springData('spring-data-jdbc', '4.1.0'), springData('spring-data-relational', '4.1.0')])

    static final Store R2DBC = new Store(
            label: 'R2DBC',
            optionKey: 'lazyval.springdata.r2dbc.converters',
            beanMethod: 'r2dbcCustomConversions',
            approvalDir: 'r2dbc',
            // the generated bean takes io.r2dbc.spi.ConnectionFactory — again not transitive
            modules: [springData('spring-data-r2dbc', '4.1.0'),
                      new Dependency('io.r2dbc', 'r2dbc-spi', '1.0.0.RELEASE')])

    static final List<Store> ALL = [CASSANDRA, MONGO, JDBC, R2DBC]

    /** The stores whose native codec generator the Spring Data generator supersedes. */
    static final List<Store> WITH_SUPERSEDED_CODEC = ALL.findAll { it.supersededCodec != null }

    /** On the classpath for every store; without a store module this alone generates nothing. */
    private static final List<Dependency> SPRING_BASELINE = [
            springData('spring-data-commons', '4.1.0'),
            new Dependency('org.springframework', 'spring-core', '7.0.8'),
            new Dependency('org.springframework', 'spring-beans', '7.0.8'),
            new Dependency('org.springframework', 'spring-context', '7.0.8')]

    static Dependency[] classpathFor(Store store) {
        (store.modules + SPRING_BASELINE) as Dependency[]
    }

    static Dependency[] baselineOnly() {
        SPRING_BASELINE as Dependency[]
    }

    private static Dependency springData(String artifact, String version) {
        new Dependency('org.springframework.data', artifact, version)
    }
}
