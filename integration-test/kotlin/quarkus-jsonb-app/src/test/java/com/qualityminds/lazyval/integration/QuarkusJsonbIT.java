package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.integration.boundary.json.Order;
import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reads and writes {@link Order} through the {@link Jsonb} configured by Quarkus, i.e. with the generated
 * {@code LazyvalJsonbAdapters} applied as {@code JsonbConfigCustomizer}. Quarkus REST uses the same {@code Jsonb}
 * bean, so no HTTP round trip is needed.
 *
 * A {@code JsonbAdapter} receives the payload already read by the JSON-B implementation, so the adapter never sees
 * a non-scalar token: every one of them is rejected by the JSON-B implementation itself.
 * couponCode sits before currency and orderDate on purpose, as in the Jackson tests: a nested value that is not
 * consumed would show up in the fields after it.
 */
@DisplayName("Kotlin - Quarkus JSON-B")
@QuarkusTest
class QuarkusJsonbIT {

    @Inject // this is already configured with the LazyvalJsonbAdapters
    Jsonb jsonb;

    @Test
    void writesDomainPrimitivesAsScalars() {
        var order = new Order(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                Isbn.parse("978-3-16-148410-0"),
                Quantity.of(2),
                new EMail("customer@example.com"),
                "EUR",
                new OrderDate(LocalDate.of(2026, 1, 1)),
                CouponCode.ofNullable("SUMMER25"));

        // asserted with JSON-P, so the assertions do not depend on the adapters under test
        JsonObject json = parse(jsonb.toJson(order));

        assertEquals("978-3-16-148410-0", json.getString("isbn"));
        assertEquals(2, json.getInt("quantity"));
        assertEquals("customer@example.com", json.getString("email"));
        assertEquals("SUMMER25", json.getString("couponCode"));
        assertEquals("2026-01-01", json.getString("orderDate"));
    }

    @Test
    void readsScalarPayloadsIntoDomainPrimitives() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": 2,
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        var order = jsonb.fromJson(json, Order.class);

        assertEquals("978-3-16-148410-0", order.getIsbn().getValue());
        // the payload is the value class Amount, whose getter is mangled - compare the whole value instead
        assertEquals(Quantity.of(2), order.getQuantity());
        assertEquals("customer@example.com", order.getEmail().getValue());
        assertEquals("SUMMER25", order.getCouponCode().getValue());
        assertEquals("EUR", order.getCurrency());
        assertEquals(LocalDate.of(2026, 1, 1), order.getOrderDate().getValue());
    }

    @Test
    void rejectsQuantityGivenAnObject() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": {"value": 2},
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsQuantityGivenAnArray() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": [2],
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsQuantityGivenABoolean() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": true,
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsQuantityGivenANonNumericString() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": "two",
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsIsbnGivenAnObject() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": {"value": "978-3-16-148410-0"},
                  "quantity": 2,
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsCouponCodeGivenAnObject() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": 2,
                  "email": "customer@example.com",
                  "couponCode": {"value": "SUMMER25"},
                  "currency": "EUR",
                  "orderDate": "2026-01-01"
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsCouponCodeGivenAnObjectAsLastProperty() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": 2,
                  "email": "customer@example.com",
                  "currency": "EUR",
                  "orderDate": "2026-01-01",
                  "couponCode": {"value": "SUMMER25"}
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    @Test
    void rejectsOrderDateGivenAnObject() {
        var json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "isbn": "978-3-16-148410-0",
                  "quantity": 2,
                  "email": "customer@example.com",
                  "couponCode": "SUMMER25",
                  "currency": "EUR",
                  "orderDate": {"value": "2026-01-01"}
                }""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Order.class));
    }

    private static JsonObject parse(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
