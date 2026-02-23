package com.qualityminds.lazyval.integration

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient // This is key in 4.x to ensure the client is bound to the server
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SpringIT {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    private val baseUrl = "/order"

    @Order(1)
    @Test
    fun testAllOrders() {
        val expected = listOf(
            OrderDto(1, "3-86680-192-0", 1, "a@b.de"),
            OrderDto(2, "978-3-86680-192-9", 1, "x@y.de")
        )

        webTestClient.get()
            .uri(baseUrl)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<OrderDto>>()
            .consumeWith { result ->
                assertEquals(expected, result.responseBody)
            }
    }

    @Order(2)
    @Test
    fun addOrder() {
        val createOrderDto = CreateOrderDto("978-3-16-148410-0", 2, "test@post.de")

        webTestClient.post()
            .uri(baseUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createOrderDto)
            .exchange()
            .expectStatus().isOk
            .expectBody<OrderDto>()
            .consumeWith { result ->
                val order = result.responseBody!!
                assertEquals(createOrderDto.isbn, order.isbn)
                assertEquals(createOrderDto.quantity, order.quantity)
                assertEquals(createOrderDto.email, order.email)
            }
    }
}