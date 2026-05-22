package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.CassandraTestResource
import com.qualityminds.lazyval.integration.TestMapper
import com.qualityminds.lazyval.integration.application.Startup
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.JSON
import com.qualityminds.lazyval.integration.client.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.client.model.CreateOrder
import com.qualityminds.lazyval.integration.client.model.ValidationProblem
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.*

import static org.junit.jupiter.api.Assertions.assertThrows

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
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
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

    @Order(5)
    @Test
    void invalidInput() {
        def createOrderDto = new CreateOrder()
                .isbn('bogus')
                .quantity(-1)
                .email('invalid')

        def ex = assertThrows(ApiException) { orderApi.createOrderCassandra(createOrderDto) }
        assert ex.code == 400

        def jsonMapper = new JSON().getMapper()
        ValidationProblem validationProblem = jsonMapper.readValue(ex.responseBody, ValidationProblem)
        assert validationProblem.status == 400

        def isbnViolation = validationProblem.violations.find { it.field == "createOrder.isbn" }
        assert isbnViolation.message == /must match "^[\d\-]{10,17}$"/
        assert isbnViolation.invalidValue == "bogus"

        def qtyViolation = validationProblem.violations.find { it.field == "createOrder.quantity" }
        assert qtyViolation.message == "must be greater than or equal to 1"
        assert qtyViolation.invalidValue == -1

        def emailViolation = validationProblem.violations.find { it.field == "createOrder.email" }
        assert emailViolation.invalidValue == "invalid"
        assert emailViolation.message == /must match "^[^\s@]+@[^\s@]+$"/
    }
}
