package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Stepwise

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Stepwise
class SpringCassandraIT extends AbstractIT {

    @LocalServerPort
    int port

    @Autowired
    TestMapper mapper

    OrderCassandraApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://localhost:$port")
        orderApi = new OrderCassandraApi(client)
    }

    def "should return all orders"() {
        when:
        def orders = orderApi.getAllOrdersCassandra()

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
        def createdOrder = mapper.toDomainOrder(orderApi.createOrderCassandra(createOrderDto))

        then:
        createdOrder.isbn == Isbn.parse(createOrderDto.getIsbn())
        createdOrder.quantity == new Quantity(createOrderDto.getQuantity())
        createdOrder.email == new EMail(createOrderDto.getEmail())
        createdOrder.id != null
    }

    def "should find order by id"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderByIdCassandra(Startup.DefaultOrderB.id))

        then:
        foundOrder == Startup.DefaultOrderB
    }

    def "should find orders by isbn"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.findOrdersByIsbnCassandra(Startup.DefaultOrderB.isbn().value()))

        then:
        foundOrder == [Startup.DefaultOrderB]
    }
}
