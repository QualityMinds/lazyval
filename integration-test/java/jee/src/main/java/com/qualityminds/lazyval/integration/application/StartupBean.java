package com.qualityminds.lazyval.integration.application;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Singleton
@Startup
public class StartupBean {

    private static Logger logger = LoggerFactory.getLogger(StartupBean.class);

    // Deterministic UUIDs so the in-container server and the test JVM agree on the seed
    // orders' identity; UUID.randomUUID() would diverge across JVMs.
    public static final Order DefaultOrderA = new Order(
            UUID.fromString("a1a1a1a1-b2b2-c3c3-d4d4-e5e5e5e5e5e5"),
            Isbn.parse("3-86680-192-0"),
            new Quantity(1),
            new EMail("a@b.de"),
            OrderDate.now(),
            null);
    public static final Order DefaultOrderB = new Order(
            UUID.fromString("f6f6f6f6-a7a7-b8b8-c9c9-d0d0d0d0d0d0"),
            Isbn.parse("978-3-86680-192-9"),
            new Quantity(1),
            new EMail("x@y.de"),
            OrderDate.now(),
            CouponCode.ofNullable("FRESH12"));

    // Field injection — @Singleton EJB also requires a public no-arg constructor, and field
    // injection sidesteps having to maintain a redundant no-arg ctor alongside an @Inject one.
    @Inject
    @Named("jpa")
    OrderRepository jpaRepository;

    @Inject
    @Named("cassandra")
    OrderRepository cassandraRepository;

    @Transactional
    @PostConstruct
    void init() {
        // push demo entities to all storages
        if(jpaRepository.findAll().isEmpty()) {
            jpaRepository.save(DefaultOrderA);
            jpaRepository.save(DefaultOrderB);
            logger.info("Initialized JPA database with demo entities");
        }
        if(cassandraRepository.findAll().isEmpty()) {
            cassandraRepository.save(DefaultOrderA);
            cassandraRepository.save(DefaultOrderB);
            logger.info("Initialized Cassandra database with demo entities");
        }
    }
}
