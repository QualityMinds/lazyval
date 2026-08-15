package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ElementKind
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import java.net.URI

@Provider
class ConstraintViolationExceptionMapper : ExceptionMapper<ConstraintViolationException> {

    @Context
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: ConstraintViolationException): Response {
        val violations = exception.constraintViolations.map {
            Violation()
                .field(fieldPath(it))
                .message(it.message)
                .invalidValue(it.invalidValue)
        }

        val body = ValidationProblem()
            .type(PROBLEM_TYPE)
            .title(TITLE)
            .status(Response.Status.BAD_REQUEST.statusCode)
            .instance(uriInfo.requestUri.path)
            .violations(violations)

        return Response.status(Response.Status.BAD_REQUEST)
            .type(APPLICATION_PROBLEM_JSON)
            .entity(body)
            .build()
    }

    /**
     * JAX-RS property paths start with the endpoint method node (e.g.
     * `createOrder.createOrder.isbn`). Strip it so the wire format matches Spring's
     * `MethodArgumentNotValidException` output (`createOrder.isbn`).
     */
    private fun fieldPath(v: ConstraintViolation<*>): String =
        v.propertyPath
            .filter { it.kind != ElementKind.METHOD }
            .joinToString(".") { it.name }

    companion object {
        private val PROBLEM_TYPE: URI =
            URI.create("https://lazyval.qualityminds.com/problems/validation")
        private const val TITLE = "Constraint Violation"
        private const val APPLICATION_PROBLEM_JSON = "application/problem+json"
    }
}
