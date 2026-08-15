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
 * The API contract offers every persistence type to every scenario; a deployment that does not
 * implement one answers 501 instead. R2DBC is served by the separate spring-app-r2dbc scenario, so
 * it stays unimplemented here.
 */
@Title("Java - Spring unsupported persistence type")
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

    def "should answer 501 for R2DBC"() {
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
