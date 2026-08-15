package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.CassandraTestResource
import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.JSON
import com.qualityminds.lazyval.integration.client.api.OrderApi
import com.qualityminds.lazyval.integration.client.model.PersistenceType
import com.qualityminds.lazyval.integration.client.model.Problem
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * The API contract offers every persistence type to every scenario. JDBC and R2DBC are Spring-only,
 * so this deployment answers 501 for them.
 */
@DisplayName("Java - Quarkus unsupported persistence type")
@QuarkusTest
@QuarkusTestResource(value = CassandraTestResource.class)
class QuarkusUnsupportedPersistenceTypeIT {

    @TestHTTPResource
    URL url

    OrderApi orderApi

    @BeforeEach
    void beforeTest() {
        def client = new ApiClient()
        client.updateBaseUri(url.toString())
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderApi(client)
    }

    @Test
    @DisplayName("should answer 501 for JDBC")
    void jdbcNotImplemented() {
        assertNotImplemented(PersistenceType.JDBC)
    }

    @Test
    @DisplayName("should answer 501 for R2DBC")
    void r2dbcNotImplemented() {
        assertNotImplemented(PersistenceType.R2DBC)
    }

    private void assertNotImplemented(PersistenceType persistenceType) {
        def ex = assertThrows(ApiException) { orderApi.getAllOrders(persistenceType) }
        assert ex.code == 501

        Problem problem = new JSON().getMapper().readValue(ex.responseBody, Problem)
        assert problem.status == 501
        assert problem.title == 'Not Implemented'
        assert problem.detail.contains(persistenceType.value)
    }
}
