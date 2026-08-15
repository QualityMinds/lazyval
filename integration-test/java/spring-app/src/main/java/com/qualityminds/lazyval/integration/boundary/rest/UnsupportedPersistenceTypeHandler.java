package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.Problem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Answers requests for a valid but unwired {@code persistenceType} with 501, per RFC 9110 §15.6.2
 * ("the server does not support the functionality required to fulfil the request"). Without this
 * advice Spring would map the exception to a bare 500.
 */
@RestControllerAdvice
public class UnsupportedPersistenceTypeHandler {

    private static final URI PROBLEM_TYPE =
            URI.create("https://lazyval.qualityminds.com/problems/unsupported-persistence-type");
    private static final String TITLE = "Not Implemented";

    @ExceptionHandler(UnsupportedPersistenceTypeException.class)
    public ResponseEntity<Problem> handleUnsupportedPersistenceType(
            UnsupportedPersistenceTypeException ex, HttpServletRequest request) {
        Problem body = new Problem()
                .type(PROBLEM_TYPE)
                .title(TITLE)
                .status(HttpStatus.NOT_IMPLEMENTED.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
