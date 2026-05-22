package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem;
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class ValidationExceptionHandler {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/validation");
    private static final String TITLE = "Constraint Violation";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationProblem> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return problemResponse(violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationProblem> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(v -> new Violation(v.getPropertyPath().toString(), v.getMessage())
                        .invalidValue(v.getInvalidValue()))
                .toList();
        return problemResponse(violations, request);
    }

    /**
     * Maps body-deserialization failures. Domain primitives (Isbn, Quantity) self-validate in
     * their constructors and throw IllegalArgumentException, which Jackson surfaces as a
     * deserialization error before bean validation gets a chance to run on the request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationProblem> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        Violation violation = new Violation("body", ex.getMostSpecificCause().getMessage());
        return problemResponse(List.of(violation), request);
    }

    private Violation toViolation(FieldError fieldError) {
        String field = fieldError.getObjectName() + "." + fieldError.getField();
        Violation v = new Violation(field, fieldError.getDefaultMessage());
        v.setInvalidValue(fieldError.getRejectedValue());
        return v;
    }

    private ResponseEntity<ValidationProblem> problemResponse(
            List<Violation> violations, HttpServletRequest request) {
        ValidationProblem body = new ValidationProblem(violations)
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(HttpStatus.BAD_REQUEST.value())
                .instance(request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
