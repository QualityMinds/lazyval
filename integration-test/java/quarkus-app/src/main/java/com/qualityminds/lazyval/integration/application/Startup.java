package com.qualityminds.lazyval.integration.application;

import com.qualityminds.lazyval.integration.domain.EMail;
import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Startup {

    private final OrderRepository jpaRepository;
    private final OrderRepository cassandraRepository;

    public static final Order DefaultOrderA = Order.create(
            Isbn.parse("3-86680-192-0"),
            new Quantity(1),
            new EMail("a@b.de"));
    public static final Order DefaultOrderB = Order.create(
            Isbn.parse("978-3-86680-192-9"),
            new Quantity(1),
            new EMail("x@y.de"));

    @Inject
    public Startup(
            @Identifier("jpa") OrderRepository jpaRepository,
            @Identifier("cassandra") OrderRepository cassandraRepository)
    {
        this.jpaRepository = jpaRepository;
        this.cassandraRepository = cassandraRepository;
    }

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        // push demo entities to all storages
        if(jpaRepository.findAll().isEmpty()) {
            jpaRepository.save(DefaultOrderA);
            jpaRepository.save(DefaultOrderB);
        }
        if(cassandraRepository.findAll().isEmpty()) {
            cassandraRepository.save(DefaultOrderA);
            cassandraRepository.save(DefaultOrderB);
        }
    }
}
