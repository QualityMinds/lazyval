package com.qualityminds.lazyval.integation


import com.qualityminds.lazyval.integration.CreateOrderDto
import com.qualityminds.lazyval.integration.EMail
import com.qualityminds.lazyval.integration.OrderDto
import com.qualityminds.lazyval.integration.OrderResource
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
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
                new OrderDto(1, Isbn.parse('3-86680-192-0'), new Quantity(1), new EMail('a@b.de')),
                new OrderDto(2, Isbn.parse('978-3-86680-192-9'), new Quantity(1), new EMail('x@y.de')),
        )
    }

    @Order(2)
    @Test
    void addOrder(){
        def createOrderDto = new CreateOrderDto(Isbn.parse('978-3-16-148410-0'), new Quantity(2), new EMail('test@post.de'))
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

    @Order(3)
    @Test
    void jacksonHandlingNull() {
        def jsonBody = """{
               "quantity": null,
               "isbn":"978-3-16-148410-0",
               "email":"test@post.de"
            }"""

        def order = given()
                .body(jsonBody)
                .contentType("application/json")
                .when()
                .post()
                .then()
                .extract().body().as(OrderDto.class)

        assert [order.isbn, order.quantity, order.email] ==
                [Isbn.parse("978-3-16-148410-0"), null, new EMail("test@post.de")]
    }
}
