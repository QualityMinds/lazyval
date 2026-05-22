package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.TestMapper
import com.qualityminds.lazyval.integration.application.StartupBean
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.JSON
import com.qualityminds.lazyval.integration.client.api.OrderJpaApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.client.model.ValidationProblem
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.mapstruct.factory.Mappers
import org.testcontainers.spock.Testcontainers
import spock.lang.Stepwise

@Testcontainers
@Stepwise
class JeeJpaIT extends AbstractLibertyIT {

    TestMapper mapper = Mappers.getMapper(TestMapper.class)
    OrderJpaApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://${liberty.host}:${liberty.getMappedPort(PORT)}")
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderJpaApi(client)
    }

    def "should return all orders"() {
        when:
        def orders = orderApi.getAllOrdersJpa()

        then:
        mapper.toDomainOrder(orders) == [StartupBean.DefaultOrderA, StartupBean.DefaultOrderB]
    }

    def "should add order"() {
        given:
        def createOrderDto = new CreateOrder()
                .isbn('978-3-16-148410-0')
                .quantity(2)
                .email('test@post.de')

        when:
        def createdOrder = mapper.toDomainOrder(orderApi.createOrderJpa(createOrderDto))

        then:
        createdOrder.isbn == Isbn.parse(createOrderDto.getIsbn())
        createdOrder.quantity == new Quantity(createOrderDto.getQuantity())
        createdOrder.email == new EMail(createOrderDto.getEmail())
        createdOrder.id != null
    }

    def "should find order by id"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderByIdJpa(StartupBean.DefaultOrderB.id))

        then:
        foundOrder == StartupBean.DefaultOrderB
    }

    def "should find orders by isbn"() {
        when:
        def foundOrders = mapper.toDomainOrder(orderApi.findOrdersByIsbnJpa(StartupBean.DefaultOrderB.isbn.value))

        then:
        foundOrders == [StartupBean.DefaultOrderB]
    }

    def "should handle invalid input"() {
        given:
        def createOrderDto = new CreateOrder()
                .isbn('bogus')
                .quantity(-1)
                .email('invalid')
        def jsonMapper = new JSON().getMapper()

        when:
        orderApi.createOrderJpa(createOrderDto)

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
}
