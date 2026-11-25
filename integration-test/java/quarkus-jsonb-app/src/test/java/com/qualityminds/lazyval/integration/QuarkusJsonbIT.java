package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.integration.boundary.rest.Demo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reads {@link Demo} through the {@link Jsonb} configured by Quarkus, i.e. with the generated
 * {@code LazyvalJsonbAdapters} applied via the {@code JsonbConfigCustomizer} SPI.
 *
 * A {@code JsonbAdapter} receives the payload already read by the JSON-B implementation, so the adapter never sees
 * a non-scalar token: every one of them is rejected before the adapter runs.
 * couponCode sits before email on purpose, so a nested value that is not consumed would show up in the field after it.
 */
@DisplayName("Java - Quarkus JSON-B adapters")
@QuarkusTest
class QuarkusJsonbIT {

    @Inject
    Jsonb jsonb;

    @Test
    void readsScalarPayloadsThroughTheConfiguredJsonb() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":5,"couponCode":"SUMMER25","email":"x@y.de"}""";

        var demo = jsonb.fromJson(json, Demo.class);

        assertEquals("978-3-86680-192-9", demo.isbn().value());
        assertEquals(5, demo.quantity().value());
        assertEquals("SUMMER25", demo.couponCode().value());
        assertEquals("x@y.de", demo.email().value());
    }

    @Test
    void rejectsQuantityGivenAnObject() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":{"value":5},"couponCode":"SUMMER25","email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }

    @Test
    void rejectsQuantityGivenAnArray() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":[5],"couponCode":"SUMMER25","email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }

    @Test
    void rejectsQuantityGivenABoolean() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":true,"couponCode":"SUMMER25","email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }

    @Test
    void rejectsQuantityGivenANonNumericString() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":"five","couponCode":"SUMMER25","email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }

    @Test
    void rejectsIsbnGivenAnObject() {
        var json = """
                {"isbn":{"value":"978-3-86680-192-9"},"quantity":5,"couponCode":"SUMMER25","email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }

    @Test
    void rejectsCouponCodeGivenAnObject() {
        var json = """
                {"isbn":"978-3-86680-192-9","quantity":5,"couponCode":{"value":"SUMMER25"},"email":"x@y.de"}""";

        assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Demo.class));
    }
}
