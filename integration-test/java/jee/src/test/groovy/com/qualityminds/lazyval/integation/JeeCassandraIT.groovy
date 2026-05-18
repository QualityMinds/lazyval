package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.TestMapper
import com.qualityminds.lazyval.integration.application.StartupBean
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.mapstruct.factory.Mappers
import org.testcontainers.spock.Testcontainers
import spock.lang.Stepwise

@Testcontainers
@Stepwise
class JeeCassandraIT extends AbstractLibertyIT {

    TestMapper mapper = Mappers.getMapper(TestMapper.class)
    OrderCassandraApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://${liberty.host}:${liberty.getMappedPort(PORT)}")
        orderApi = new OrderCassandraApi(client)
    }

    def "should return all orders"() {
        when:
        def orders = orderApi.getAllOrdersCassandra()

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
        def createdOrder = mapper.toDomainOrder(orderApi.createOrderCassandra(createOrderDto))

        then:
        createdOrder.isbn() == Isbn.parse(createOrderDto.getIsbn())
        createdOrder.quantity() == new Quantity(createOrderDto.getQuantity())
        createdOrder.email() == new EMail(createOrderDto.getEmail())
        createdOrder.id() != null
    }

    def "should find order by id"() {
        when:
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderByIdCassandra(StartupBean.DefaultOrderB.id()))

        then:
        foundOrder == StartupBean.DefaultOrderB
    }

    def "should find orders by isbn"() {
        when:
        def foundOrders = mapper.toDomainOrder(orderApi.findOrdersByIsbnCassandra(StartupBean.DefaultOrderB.isbn().value()))

        then:
        foundOrders == [StartupBean.DefaultOrderB]
    }
}
