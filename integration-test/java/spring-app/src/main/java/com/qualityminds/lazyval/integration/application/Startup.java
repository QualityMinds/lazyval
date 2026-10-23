package com.qualityminds.lazyval.integration.application;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Component
public class Startup {

    public static final Order DefaultOrderA = Order.create(
            Isbn.parse("3-86680-192-0"),
            new Quantity(1),
            new EMail("a@b.de"),
            Currency.getInstance("EUR")
    );

    public static final Order DefaultOrderB = Order.create(
            Isbn.parse("978-3-86680-192-9"),
            new Quantity(1),
            new EMail("x@y.de"),
            Currency.getInstance("USD")
    );

    private final OrderRepository jpaRepository;
    private final OrderRepository cassandraRepository;
    private final OrderRepository mongoRepository;
    private final OrderRepository jdbcRepository;
    private final OrderRepository redisRepository;

    public Startup(@Qualifier("jpa") OrderRepository jpaRepository,
                   @Qualifier("cassandra") OrderRepository cassandraRepository,
                   @Qualifier("mongo") OrderRepository mongoRepository,
                   @Qualifier("jdbc") OrderRepository jdbcRepository,
                   @Qualifier("redis") OrderRepository redisRepository) {
        this.jpaRepository = jpaRepository;
        this.cassandraRepository = cassandraRepository;
        this.mongoRepository = mongoRepository;
        this.jdbcRepository = jdbcRepository;
        this.redisRepository = redisRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStart() {
        if (jpaRepository.findAll().isEmpty()) {
            jpaRepository.save(DefaultOrderA);
            jpaRepository.save(DefaultOrderB);
        }
        if (cassandraRepository.findAll().isEmpty()) {
            cassandraRepository.save(DefaultOrderA);
            cassandraRepository.save(DefaultOrderB);
        }
        if (mongoRepository.findAll().isEmpty()) {
            mongoRepository.save(DefaultOrderA);
            mongoRepository.save(DefaultOrderB);
        }
        if (jdbcRepository.findAll().isEmpty()) {
            jdbcRepository.save(DefaultOrderA);
            jdbcRepository.save(DefaultOrderB);
        }
        if (redisRepository.findAll().isEmpty()) {
            redisRepository.save(DefaultOrderA);
            redisRepository.save(DefaultOrderB);
        }
    }
}
