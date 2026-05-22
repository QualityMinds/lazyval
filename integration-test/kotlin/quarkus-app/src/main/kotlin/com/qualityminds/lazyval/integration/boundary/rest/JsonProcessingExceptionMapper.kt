package com.qualityminds.lazyval.integration.boundary.rest

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.net.URI

/**
 * Maps Jackson deserialization failures to RFC 9457 ValidationProblem.
 * Domain primitives (Isbn, Quantity) self-validate in their constructors and throw
 * IllegalArgumentException; Quarkus REST's ServerJacksonMessageBodyReader wraps the
 * resulting JsonProcessingException in WebApplicationException(400), which is what
 * we have to inspect here.
 */
@Provider
@Priority(Priorities.USER)
class JsonProcessingExceptionMapper : ExceptionMapper<WebApplicationException> {

    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: WebApplicationException): Response {
        val jpe = exception.findJsonProcessingException() ?: return exception.response

        val violation = Violation(jpe.fieldPath(), jpe.rootMessage())

        val body = ValidationProblem(listOf(violation))
            .type(PROBLEM_TYPE)
            .title(TITLE)
            .status(Response.Status.BAD_REQUEST.statusCode)
            .instance(uriInfo.requestUri.path)

        return Response.status(Response.Status.BAD_REQUEST)
            .type(APPLICATION_PROBLEM_JSON)
            .entity(body)
            .build()
    }

    private fun Throwable.findJsonProcessingException(): JsonProcessingException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is JsonProcessingException) return current
            if (current.cause === current) return null
            current = current.cause
        }
        return null
    }

    private fun JsonProcessingException.fieldPath(): String {
        if (this is JsonMappingException && path.isNotEmpty()) {
            val joined = path.joinToString(".") { it.fieldName ?: "" }
                .trim('.')
            if (joined.isNotEmpty()) return joined
        }
        return "body"
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
