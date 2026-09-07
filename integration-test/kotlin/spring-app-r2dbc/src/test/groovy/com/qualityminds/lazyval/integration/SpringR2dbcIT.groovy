package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.api.OrderApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.client.model.PersistenceType
import com.qualityminds.lazyval.integration.client.model.ValidationProblem
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Stepwise
import spock.lang.Title
import tools.jackson.databind.ObjectMapper

/**
 * Same contract and same blocking client as the other scenarios, served by a fully reactive stack:
 * WebFlux, Mono/Flux controller signatures, and Spring Data R2DBC over PostgreSQL. What is under
 * test is that lazyval's generated R2DBC converters and Jackson serdes keep the value semantics of
 * the domain primitives intact across that stack.
 */
@Title("Kotlin - Spring-Data R2DBC (reactive)")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Stepwise
class SpringR2dbcIT extends AbstractIT {

    @LocalServerPort
    int port

    @Autowired
    TestMapper mapper
    @Autowired
    ObjectMapper jsonMapper

    static final PersistenceType PERSISTENCE_TYPE = PersistenceType.R2DBC

    OrderApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://localhost:$port")
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderApi(client)
    }

    def "should return all orders"() {
        when:
        def orders = orderApi.getAllOrders(PERSISTENCE_TYPE)

        then:
        mapper.toDomainOrder(orders) == [Startup.DefaultOrderA, Startup.DefaultOrderB]
    }

    def "should add order"() {
        given:
        def createOrderDto = new CreateOrder()
                .isbn('978-3-16-148410-0')
                .quantity(2)
                .email('test@post.de')

        when:
        def createdOrder = mapper.toDomainOrder(orderApi.createOrder(PERSISTENCE_TYPE, createOrderDto))

        then:
        createdOrder.isbn == Isbn.parse(createOrderDto.getIsbn())
        createdOrder.quantity == Quantity.of(createOrderDto.getQuantity())
        createdOrder.email == new EMail(createOrderDto.getEmail())
        createdOrder.id != null
        createdOrder.couponCode == null
    }

    def "should handle invalid input"() {
        given:
        def createOrderDto = new CreateOrder()
                .isbn('bogus')
                .quantity(-1)
                .email('invalid')

        when:
        orderApi.createOrder(PERSISTENCE_TYPE, createOrderDto)

        then:
        def ex = thrown(ApiException)
        ex.code == 400

        and: 'problem-json is present'
        ValidationProblem validationProblem = jsonMapper.readValue(ex.responseBody, ValidationProblem)
        validationProblem.status == 400

        and: 'every rejected field is reported with the value that was rejected'
        // the messages themselves are asserted in the blocking spring-app scenario: WebFlux resolves
        // the request locale per exchange rather than through LocaleContextHolder, so the wording is
        // not pinned here
        with(validationProblem.violations.find { it.field == "createOrder.isbn" }) {
            invalidValue == "bogus"
        }
        with(validationProblem.violations.find { it.field == "createOrder.quantity" }) {
            invalidValue == -1
        }
        with(validationProblem.violations.find { it.field == "createOrder.email" }) {
            invalidValue == "invalid"
        }
    }

    def "should find order by id"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderById(PERSISTENCE_TYPE, Startup.DefaultOrderB.id))

        then:
        foundOrder == Startup.DefaultOrderB
    }

    def "should find orders by isbn"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.findOrdersByIsbn(PERSISTENCE_TYPE, Startup.DefaultOrderB.isbn.value))

        then:
        foundOrder == [Startup.DefaultOrderB]
    }
}
