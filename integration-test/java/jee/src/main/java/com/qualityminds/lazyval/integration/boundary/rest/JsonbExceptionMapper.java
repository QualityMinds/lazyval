package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem;
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.List;

/**
 * Maps JSON-B deserialization failures to RFC 9457 ValidationProblem.
 * Domain primitives (Isbn, Quantity) self-validate in their constructors and throw
 * IllegalArgumentException, which JSON-B surfaces as JsonbException before bean
 * validation gets a chance to run on the request body.
 */
@Provider
public class JsonbExceptionMapper implements ExceptionMapper<JsonbException> {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/validation");
    private static final String TITLE = "Constraint Violation";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(JsonbException exception) {
        Violation violation = new Violation()
                .field("body")
                .message(rootMessage(exception));

        ValidationProblem body = new ValidationProblem()
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(Response.Status.BAD_REQUEST.getStatusCode())
                .instance(uriInfo != null ? uriInfo.getRequestUri().getPath() : null)
                .violations(List.of(violation));

        return Response.status(Response.Status.BAD_REQUEST)
                .type(APPLICATION_PROBLEM_JSON)
                .entity(body)
                .build();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
