package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The same key layout Spring Data Redis uses for its repositories, built by hand: one value per order
 * under {@code orders:<id>}, the set {@code orders} of all ids, and {@code orders:isbn:<isbn>} as the
 * secondary index for {@link #findByIsbn}.
 */
@ApplicationScoped
@Identifier("redis")
public class RedisOrderRepository implements OrderRepository {

    private static final String ALL = "orders";

    private final ValueCommands<String, RedisOrder> orders;
    private final SetCommands<String, UUID> ids;
    private final RedisMapper mapper;

    @Inject
    RedisOrderRepository(RedisDataSource dataSource, RedisMapper mapper) {
        this.orders = dataSource.value(RedisOrder.class);
        this.ids = dataSource.set(UUID.class);
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        RedisOrder entity = mapper.toDB(order);
        orders.set(orderKey(entity.id()), entity);
        ids.sadd(ALL, entity.id());
        ids.sadd(isbnKey(entity.isbn()), entity.id());
    }

    @Override
    public List<Order> findAll() {
        return load(ids.smembers(ALL)).stream()
                .sorted(Comparator.comparing(o -> o.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        RedisOrder entity = orders.get(orderKey(id));
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return load(ids.smembers(isbnKey(isbn)));
    }

    private List<Order> load(Set<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        String[] keys = orderIds.stream().map(RedisOrderRepository::orderKey).toArray(String[]::new);
        return mapper.toDomain(orders.mget(keys).values().stream().filter(Objects::nonNull).toList());
    }

    private static String orderKey(UUID id) {
        return "orders:" + id;
    }

    private static String isbnKey(Isbn isbn) {
        return "orders:isbn:" + isbn.value();
    }
}
