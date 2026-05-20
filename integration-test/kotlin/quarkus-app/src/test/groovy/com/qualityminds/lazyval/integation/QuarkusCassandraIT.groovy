package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.CassandraTestResource
import com.qualityminds.lazyval.integration.TestMapper
import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.*

/**
 * When this test completes, the generated classes properly mapped the types to and from the DB as well
 * as converted them to
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation)
@QuarkusTestResource(value = CassandraTestResource.class)
class QuarkusCassandraIT {

    @TestHTTPResource
    URL url
    @Inject
    TestMapper mapper

    OrderCassandraApi orderApi

    @BeforeEach
    void beforeTest(){
        def client = new ApiClient()
        client.updateBaseUri(url.toString())
        orderApi = new OrderCassandraApi(client)
    }

    @Order(1)
    @Test
    void testAllOrders(){
        def orders = orderApi.getAllOrdersCassandra()

        assert mapper.toDomainOrder(orders) == [Startup.DefaultOrderA, Startup.DefaultOrderB]
    }

    @Order(2)
    @Test
    void addOrder(){
        def createOrderDto = new CreateOrder()
                .isbn('978-3-16-148410-0')
                .quantity(2)
                .email('test@post.de')

        def createdOrder = mapper.toDomainOrder(orderApi.createOrderCassandra(createOrderDto))

        assert createdOrder.isbn == Isbn.parse(createOrderDto.getIsbn())
        assert createdOrder.quantity == new Quantity(createOrderDto.getQuantity())
        assert createdOrder.email == new EMail(createOrderDto.getEmail())
        assert createdOrder.id != null
    }

    @Order(3)
    @Test
    void findById(){
        def foundOrder = mapper.toDomainOrder(orderApi.getOrderByIdCassandra(Startup.DefaultOrderB.id))

        assert foundOrder == Startup.DefaultOrderB
    }

    @Order(4)
    @Test
    void findByIsbn(){
        def foundOrder = mapper.toDomainOrder(orderApi.findOrdersByIsbnCassandra(Startup.DefaultOrderB.isbn.value))

        assert foundOrder == [ Startup.DefaultOrderB ]
    }
}
