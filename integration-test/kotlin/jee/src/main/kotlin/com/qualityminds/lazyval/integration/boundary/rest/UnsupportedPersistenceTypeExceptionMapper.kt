package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.Problem
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.net.URI

/**
 * Answers requests for a valid but unwired `persistenceType` with 501, per RFC 9110 §15.6.2
 * ("the server does not support the functionality required to fulfil the request"). JDBC and R2DBC
 * are only implemented in the Spring scenario.
 */
@Provider
@Priority(Priorities.USER)
class UnsupportedPersistenceTypeExceptionMapper : ExceptionMapper<UnsupportedPersistenceTypeException> {

    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: UnsupportedPersistenceTypeException): Response {
        val body = Problem()
            .type(PROBLEM_TYPE)
            .title(TITLE)
            .status(Response.Status.NOT_IMPLEMENTED.statusCode)
            .detail(exception.message)
            .instance(uriInfo.requestUri.path)

        return Response.status(Response.Status.NOT_IMPLEMENTED)
            .type(APPLICATION_PROBLEM_JSON)
            .entity(body)
            .build()
    }

    companion object {
        private val PROBLEM_TYPE: URI =
            URI.create("https://lazyval.qualityminds.com/problems/unsupported-persistence-type")
        private const val TITLE = "Not Implemented"
        private const val APPLICATION_PROBLEM_JSON = "application/problem+json"
    }
}
