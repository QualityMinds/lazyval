package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.api.OrderMongoApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
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

@Title("Java - Spring-Data MongoDB")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Stepwise
class SpringMongoIT extends AbstractIT {

    @LocalServerPort
    int port

    @Autowired
    TestMapper mapper
    @Autowired
    ObjectMapper jsonMapper

    OrderMongoApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://localhost:$port")
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderMongoApi(client)
    }

    def "should return all orders"() {
        when:
        def orders = orderApi.getAllOrdersMongo()

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
        def createdOrder = mapper.toDomainOrder(orderApi.createOrderMongo(createOrderDto))

        then:
        createdOrder.isbn == Isbn.parse(createOrderDto.getIsbn())
        createdOrder.quantity == new Quantity(createOrderDto.getQuantity())
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
        orderApi.createOrderMongo(createOrderDto)

        then:
        def ex = thrown(ApiException)
        ex.code == 400

        and: 'problem-json is present'
        ValidationProblem validationProblem = jsonMapper.readValue(ex.responseBody, ValidationProblem)
        validationProblem.status == 400
        and: 'ibsn is reported'
        with(validationProblem.violations.find { it.field == "createOrder.isbn" }) {
            message == /must match "^[\d\-]{10,17}$"/
            invalidValue == "bogus"
        }
        and: 'quantity is reported'
        with(validationProblem.violations.find { it.field == "createOrder.quantity" }) {
            message == "must be greater than or equal to 1"
            invalidValue == -1
        }
        and: 'email is reported'
        with(validationProblem.violations.find { it.field == "createOrder.email" }) {
            invalidValue == "invalid"
            message == /must match "^[^\s@]+@[^\s@]+$"/
        }
    }

    def "should find order by id"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderByIdMongo(Startup.DefaultOrderB.id))

        then:
        foundOrder == Startup.DefaultOrderB
    }

    def "should find orders by isbn"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.findOrdersByIsbnMongo(Startup.DefaultOrderB.isbn().value()))

        then:
        foundOrder == [Startup.DefaultOrderB]
    }
}
