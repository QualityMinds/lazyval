package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.CreateOrderDto
import com.qualityminds.lazyval.integration.OrderDto
import com.qualityminds.lazyval.integration.OrderResource
import io.quarkus.test.common.http.TestHTTPEndpoint
import io.quarkus.test.junit.QuarkusTest
import io.restassured.common.mapper.TypeRef
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

import static io.restassured.RestAssured.given
import static io.restassured.RestAssured.when

/**
 * When this test completes, the generated classes properly mapped the types to and from the DB as well
 * as converted them to
 */
@QuarkusTest
@TestHTTPEndpoint(OrderResource)
@TestMethodOrder(MethodOrderer.OrderAnnotation)
class QuarkusIT {

    @Order(1)
    @Test
    void testAllOrders(){
        def orders = when().get()
                .then()
                .statusCode(200)
                .extract().body().as(new TypeRef<List<OrderDto>>() {})
        assert orders == List.of(
                new OrderDto(1, '3-86680-192-0', 1, 'a@b.de'),
                new OrderDto(2, '978-3-86680-192-9', 1, 'x@y.de'),
        )
    }

    @Order(2)
    @Test
    void addOrder(){
        def createOrderDto = new CreateOrderDto('978-3-16-148410-0', 2, 'test@post.de')
        def order = given()
                .body(createOrderDto)
                .contentType("application/json")
        .when()
                .post()
        .then()
                .statusCode(200)
                .extract().body().as(OrderDto.class)

        assert [order.isbn, order.quantity, order.email] ==
                [createOrderDto.isbn, createOrderDto.quantity, createOrderDto.email]
    }
}
