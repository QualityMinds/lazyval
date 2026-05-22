package com.qualityminds.lazyval.integration.boundary.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem;
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps Jackson deserialization failures to RFC 9457 ValidationProblem.
 * Domain primitives (Isbn, Quantity) self-validate in their constructors and throw
 * IllegalArgumentException, which Jackson surfaces as JsonProcessingException. Quarkus REST's
 * ServerJacksonMessageBodyReader wraps that in WebApplicationException(400) before any
 * exception mapper sees it, so we recognise the wrapper here as well.
 */
@Provider
@Priority(Priorities.USER)
public class JsonProcessingExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/validation");
    private static final String TITLE = "Constraint Violation";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        JsonProcessingException jpe = findJsonProcessingException(exception);
        if (jpe == null) {
            return exception.getResponse();
        }

        Violation violation = new Violation(fieldPath(jpe), rootMessage(jpe));

        ValidationProblem body = new ValidationProblem(List.of(violation))
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(Response.Status.BAD_REQUEST.getStatusCode())
                .instance(uriInfo != null ? uriInfo.getRequestUri().getPath() : null);

        return Response.status(Response.Status.BAD_REQUEST)
                .type(APPLICATION_PROBLEM_JSON)
                .entity(body)
                .build();
    }

    private static JsonProcessingException findJsonProcessingException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof JsonProcessingException jpe) {
                return jpe;
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    private static String fieldPath(JsonProcessingException exception) {
        if (exception instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String joined = mapping.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.joining("."));
            if (!joined.isEmpty()) return joined;
        }
        return "body";
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
