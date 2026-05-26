package com.qualityminds.lazyval.integration;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the generated {@code LazyvalJsonbAdapters} is picked up by Quarkus
 * via the {@code JsonbConfigCustomizer} SPI and serializes/deserializes domain-primitives
 * as scalar values rather than nested objects.
 *
 * Asserts JSON shape directly with JSON-P so the test does not depend on the same
 * adapters it is verifying.
 */
@DisplayName("Kotlin - Quarkus JSON-B")
@QuarkusTest
class DemoResourceIT {

    @TestHTTPResource("/demo")
    URI demoUri;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void serializesLazyValuesAsScalars() throws Exception {
        var response = http.send(
                HttpRequest.newBuilder(demoUri).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonObject json = parse(response.body());
        assertEquals("3-86680-192-0", json.getString("isbn"));
        assertEquals(2, json.getInt("quantity"));
        assertEquals("a@b.de", json.getString("email"));
    }

    @Test
    void deserializesScalarsBackIntoLazyValues() throws Exception {
        String payload = "{\"isbn\":\"978-3-86680-192-9\",\"quantity\":5,\"email\":\"x@y.de\"}";

        var response = http.send(
                HttpRequest.newBuilder(demoUri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonObject json = parse(response.body());
        assertEquals("978-3-86680-192-9", json.getString("isbn"));
        assertEquals(5, json.getInt("quantity"));
        assertEquals("x@y.de", json.getString("email"));
    }

    private static JsonObject parse(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
