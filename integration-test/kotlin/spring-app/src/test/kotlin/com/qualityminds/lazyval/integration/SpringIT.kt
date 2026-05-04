package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
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
import java.time.LocalDate
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
            OrderDto(1, Isbn.parse("3-86680-192-0"), Quantity(1), EMail("a@b.de"), OrderDate.now()),
            OrderDto(2, Isbn.parse("978-3-86680-192-9"), Quantity(1), EMail("x@y.de"), OrderDate.now())
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
        val createOrderDto = CreateOrderDto(Isbn.parse("978-3-16-148410-0"), Quantity(2), EMail("test@post.de"))

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
                assertEquals(OrderDate(LocalDate.now()), order.orderDate)
            }
    }

    @Order(3)
    @Test
    fun jacksonHandlingNull() {
        val jsonBody = """{
            |   "quantity":null,
            |   "isbn":"978-3-16-148410-0",
            |   "email":"test@post.de"
            |}""".trimMargin()

        webTestClient.post()
            .uri(baseUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(jsonBody)
            .exchange()
            .expectStatus().isBadRequest
    }
}