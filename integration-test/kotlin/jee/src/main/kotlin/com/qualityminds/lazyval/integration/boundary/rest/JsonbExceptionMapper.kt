package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation
import jakarta.json.bind.JsonbException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.net.URI

/**
 * Maps JSON-B deserialization failures to RFC 9457 ValidationProblem.
 * Domain primitives (Isbn, Quantity) self-validate in their constructors and throw
 * IllegalArgumentException, which JSON-B surfaces as JsonbException before bean
 * validation gets a chance to run on the request body.
 */
@Provider
class JsonbExceptionMapper : ExceptionMapper<JsonbException> {

    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: JsonbException): Response {
        val violation = Violation()
            .field("body")
            .message(exception.rootMessage())

        val body = ValidationProblem()
            .type(PROBLEM_TYPE)
            .title(TITLE)
            .status(Response.Status.BAD_REQUEST.statusCode)
            .instance(uriInfo.requestUri.path)
            .violations(listOf(violation))

        return Response.status(Response.Status.BAD_REQUEST)
            .type(APPLICATION_PROBLEM_JSON)
            .entity(body)
            .build()
    }

    private fun Throwable.rootMessage(): String? {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current.message
    }

    companion object {
        private val PROBLEM_TYPE: URI =
            URI.create("https://lazyval.qualityminds.com/problems/validation")
        private const val TITLE = "Constraint Violation"
        private const val APPLICATION_PROBLEM_JSON = "application/problem+json"
    }
}
