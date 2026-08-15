package com.qualityminds.lazyval.integation

import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.JSON
import com.qualityminds.lazyval.integration.client.api.OrderApi
import com.qualityminds.lazyval.integration.client.model.PersistenceType
import com.qualityminds.lazyval.integration.client.model.Problem
import org.testcontainers.spock.Testcontainers
import spock.lang.Title

/**
 * The API contract offers every persistence type to every scenario. JDBC and R2DBC are Spring-only,
 * so this deployment answers 501 for them.
 */
@Title("Java - JakartaEE unsupported persistence type")
@Testcontainers
class JeeUnsupportedPersistenceTypeIT extends AbstractLibertyIT {

    OrderApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://${liberty.host}:${liberty.getMappedPort(PORT)}")
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderApi(client)
    }

    def "should answer 501 for JDBC"() {
        given:
        def jsonMapper = new JSON().getMapper()

        when:
        orderApi.getAllOrders(PersistenceType.JDBC)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            title == 'Not Implemented'
            detail.contains('JDBC')
        }
    }

    def "should answer 501 for R2DBC"() {
        given:
        def jsonMapper = new JSON().getMapper()

        when:
        orderApi.getAllOrders(PersistenceType.R2DBC)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            title == 'Not Implemented'
            detail.contains('R2DBC')
        }
    }
}
