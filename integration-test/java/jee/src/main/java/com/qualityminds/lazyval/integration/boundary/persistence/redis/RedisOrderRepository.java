package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Writes an order the way Spring Data Redis lays out a repository entity: a hash {@code orders:<id>} with
 * one field per property, the set {@code orders} of all ids, and {@code orders:isbn:<isbn>} as the
 * secondary index for {@link #findByIsbn}.
 * <p>
 * Lettuce has one codec per connection, so the hash goes through a {@code byte[]} connection and every
 * domain-primitive field is encoded with its generated codec. {@link Currency} is no domain-primitive,
 * so the application encodes it itself.
 */
@ApplicationScoped
@Named("redis")
public class RedisOrderRepository implements OrderRepository {

    private static final String ALL = "orders";

    private final StatefulRedisConnection<String, byte[]> hashes;
    private final StatefulRedisConnection<String, String> index;

    @Inject
    RedisOrderRepository(RedisClient client) {
        this.hashes = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        this.index = client.connect(StringCodec.UTF8);
    }

    @PreDestroy
    void close() {
        hashes.close();
        index.close();
    }

    @Override
    public void save(Order order) {
        Map<String, byte[]> fields = new LinkedHashMap<>();
        fields.put("isbn", bytes(LazyvalLettuceCodecs.ISBN.encodeValue(order.isbn())));
        fields.put("quantity", bytes(LazyvalLettuceCodecs.QUANTITY.encodeValue(order.quantity())));
        fields.put("email", bytes(LazyvalLettuceCodecs.E_MAIL.encodeValue(order.email())));
        fields.put("currency", order.currency().getCurrencyCode().getBytes(StandardCharsets.UTF_8));
        fields.put("orderDate", bytes(LazyvalLettuceCodecs.ORDER_DATE.encodeValue(order.orderDate())));
        if (order.couponCode() != null) {
            fields.put("couponCode", bytes(LazyvalLettuceCodecs.COUPON_CODE.encodeValue(order.couponCode())));
        }
        String id = order.id().toString();
        hashes.sync().hset(orderKey(id), fields);
        index.sync().sadd(ALL, id);
        index.sync().sadd(isbnKey(order.isbn()), id);
    }

    @Override
    public List<Order> findAll() {
        return load(index.sync().smembers(ALL)).stream()
                .sorted(Comparator.comparing(order -> order.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return read(id.toString());
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return load(index.sync().smembers(isbnKey(isbn)));
    }

    private List<Order> load(Set<String> ids) {
        return ids.stream().map(this::read).filter(Objects::nonNull).toList();
    }

    private Order read(String id) {
        Map<String, byte[]> fields = hashes.sync().hgetall(orderKey(id));
        if (fields.isEmpty()) {
            return null;
        }
        byte[] couponCode = fields.get("couponCode");
        return new Order(
                UUID.fromString(id),
                LazyvalLettuceCodecs.ISBN.decodeValue(ByteBuffer.wrap(fields.get("isbn"))),
                LazyvalLettuceCodecs.QUANTITY.decodeValue(ByteBuffer.wrap(fields.get("quantity"))),
                LazyvalLettuceCodecs.E_MAIL.decodeValue(ByteBuffer.wrap(fields.get("email"))),
                Currency.getInstance(new String(fields.get("currency"), StandardCharsets.UTF_8)),
                LazyvalLettuceCodecs.ORDER_DATE.decodeValue(ByteBuffer.wrap(fields.get("orderDate"))),
                couponCode == null ? null : LazyvalLettuceCodecs.COUPON_CODE.decodeValue(ByteBuffer.wrap(couponCode)));
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static String orderKey(String id) {
        return "orders:" + id;
    }

    private static String isbnKey(Isbn isbn) {
        return "orders:isbn:" + isbn.value();
    }
}
