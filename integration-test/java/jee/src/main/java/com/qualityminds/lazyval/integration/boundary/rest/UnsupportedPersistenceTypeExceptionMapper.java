package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.Problem;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;

/**
 * Answers requests for a valid but unwired {@code persistenceType} with 501, per RFC 9110 §15.6.2
 * ("the server does not support the functionality required to fulfil the request"). JDBC and R2DBC
 * are only implemented in the Spring scenario.
 */
@Provider
@Priority(Priorities.USER)
public class UnsupportedPersistenceTypeExceptionMapper
        implements ExceptionMapper<UnsupportedPersistenceTypeException> {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/unsupported-persistence-type");
    private static final String TITLE = "Not Implemented";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(UnsupportedPersistenceTypeException exception) {
        Problem body = new Problem()
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(Response.Status.NOT_IMPLEMENTED.getStatusCode())
                .detail(exception.getMessage())
                .instance(uriInfo != null ? uriInfo.getRequestUri().getPath() : null);

        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .type(APPLICATION_PROBLEM_JSON)
                .entity(body)
                .build();
    }
}
