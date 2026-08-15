package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.client.ApiClient
import com.qualityminds.lazyval.integration.client.ApiException
import com.qualityminds.lazyval.integration.client.api.OrderApi
import com.qualityminds.lazyval.integration.client.model.PersistenceType
import com.qualityminds.lazyval.integration.client.model.Problem
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Title
import tools.jackson.databind.ObjectMapper

/**
 * This scenario implements R2DBC only; the other four types are valid values in the shared contract
 * and answer 501. Verifies that the reactive stack maps the error signal the same way the blocking
 * one maps a thrown exception.
 */
@Title("Java - Spring R2DBC unsupported persistence type")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringUnsupportedPersistenceTypeIT extends AbstractIT {

    @LocalServerPort
    int port

    @Autowired
    ObjectMapper jsonMapper

    OrderApi orderApi

    def setup() {
        def client = new ApiClient()
        client.updateBaseUri("http://localhost:$port")
        client.setRequestInterceptor { it.header('Accept-Language', 'en') }
        orderApi = new OrderApi(client)
    }

    def "should answer 501 for JPA"() {
        when:
        orderApi.getAllOrders(PersistenceType.JPA)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            title == 'Not Implemented'
            detail.contains('JPA')
        }
    }

    def "should answer 501 for CASSANDRA"() {
        when:
        orderApi.getAllOrders(PersistenceType.CASSANDRA)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            detail.contains('CASSANDRA')
        }
    }

    def "should answer 501 for MONGO"() {
        when:
        orderApi.getAllOrders(PersistenceType.MONGO)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            detail.contains('MONGO')
        }
    }

    def "should answer 501 for JDBC"() {
        when:
        orderApi.getAllOrders(PersistenceType.JDBC)

        then:
        def ex = thrown(ApiException)
        ex.code == 501

        and: 'problem-json is present'
        with(jsonMapper.readValue(ex.responseBody, Problem)) {
            status == 501
            detail.contains('JDBC')
        }
    }
}
