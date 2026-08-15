package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem;
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.List;

/**
 * WebFlux variant of the advice in {@code spring-app}. The exception types differ from the servlet
 * stack: body validation surfaces as {@link org.springframework.web.bind.support.WebExchangeBindException}
 * (a {@link ServerWebInputException}) rather than {@code MethodArgumentNotValidException}, and
 * decoding failures surface as {@link ServerWebInputException} rather than
 * {@code HttpMessageNotReadableException}.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/validation");
    private static final String TITLE = "Constraint Violation";
    private static final String REACTIVE_OBJECT_NAME_SUFFIX = "Mono";

    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException.class)
    public ResponseEntity<ValidationProblem> handleBindException(
            org.springframework.web.bind.support.WebExchangeBindException ex, ServerWebExchange exchange) {
        List<Violation> violations = ex.getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return problemResponse(violations, exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationProblem> handleConstraintViolation(
            ConstraintViolationException ex, ServerWebExchange exchange) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(v -> new Violation(v.getPropertyPath().toString(), v.getMessage())
                        .invalidValue(v.getInvalidValue()))
                .toList();
        return problemResponse(violations, exchange);
    }

    /**
     * Maps body-decoding failures. Domain primitives (Isbn, Quantity) self-validate in their
     * constructors and throw IllegalArgumentException, which Jackson surfaces as a decoding error
     * before bean validation gets a chance to run on the request body.
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ValidationProblem> handleInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {
        Throwable cause = ex.getMostSpecificCause();
        Violation violation = new Violation("body", cause.getMessage());
        return problemResponse(List.of(violation), exchange);
    }

    /**
     * WebFlux names the binding target after the reactive wrapper of the body parameter, so a
     * {@code Mono<CreateOrder> createOrder} parameter yields {@code createOrderMono.isbn}. The
     * suffix is stripped so the wire format matches the blocking scenarios and the JAX-RS ones,
     * which normalize their own framework-specific prefix for the same reason.
     */
    private Violation toViolation(FieldError fieldError) {
        String objectName = fieldError.getObjectName();
        if (objectName.endsWith(REACTIVE_OBJECT_NAME_SUFFIX)) {
            objectName = objectName.substring(0, objectName.length() - REACTIVE_OBJECT_NAME_SUFFIX.length());
        }
        Violation v = new Violation(objectName + "." + fieldError.getField(), fieldError.getDefaultMessage());
        v.setInvalidValue(fieldError.getRejectedValue());
        return v;
    }

    private ResponseEntity<ValidationProblem> problemResponse(
            List<Violation> violations, ServerWebExchange exchange) {
        ValidationProblem body = new ValidationProblem(violations)
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(HttpStatus.BAD_REQUEST.value())
                .instance(exchange.getRequest().getPath().value());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
