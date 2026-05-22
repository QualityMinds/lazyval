package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem;
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Provider
@Priority(Priorities.USER)
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/validation");
    private static final String TITLE = "Constraint Violation";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<Violation> violations = exception.getConstraintViolations().stream()
                .map(v -> new Violation(fieldPath(v), v.getMessage())
                        .invalidValue(v.getInvalidValue()))
                .toList();

        ValidationProblem body = new ValidationProblem(violations)
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(Response.Status.BAD_REQUEST.getStatusCode())
                .instance(uriInfo != null ? uriInfo.getRequestUri().getPath() : null);

        return Response.status(Response.Status.BAD_REQUEST)
                .type(APPLICATION_PROBLEM_JSON)
                .entity(body)
                .build();
    }

    /**
     * JAX-RS property paths start with the endpoint method node (e.g. {@code
     * createOrderJpa.createOrder.isbn}). Strip it so the wire format matches Spring's
     * {@code MethodArgumentNotValidException} output ({@code createOrder.isbn}).
     */
    private static String fieldPath(ConstraintViolation<?> v) {
        return StreamSupport.stream(v.getPropertyPath().spliterator(), false)
                .filter(node -> node.getKind() != ElementKind.METHOD)
                .map(Path.Node::getName)
                .collect(Collectors.joining("."));
    }
}
