package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import java.net.URI

/**
 * WebFlux variant of the advice in kotlin/spring-app. The exception types differ from the servlet
 * stack: body validation surfaces as [WebExchangeBindException] (a [ServerWebInputException]) rather
 * than `MethodArgumentNotValidException`, and decoding failures as [ServerWebInputException] rather
 * than `HttpMessageNotReadableException`.
 */
@RestControllerAdvice
class ValidationExceptionHandler {

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleBindException(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ValidationProblem> =
        problemResponse(ex.fieldErrors.map { it.toViolation() }, exchange)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ValidationProblem> {
        val violations = ex.constraintViolations.map {
            Violation(it.propertyPath.toString(), it.message).invalidValue(it.invalidValue)
        }
        return problemResponse(violations, exchange)
    }

    /**
     * Maps body-decoding failures. Domain primitives (Isbn, Quantity) self-validate in their
     * constructors and throw IllegalArgumentException, which Jackson surfaces as a decoding error
     * before bean validation gets a chance to run on the request body.
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInputException(
        ex: ServerWebInputException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ValidationProblem> =
        problemResponse(listOf(Violation("body", ex.mostSpecificCause.message ?: "")), exchange)

    /**
     * WebFlux names the binding target after the reactive wrapper of the body parameter, so a
     * `Mono<CreateOrder> createOrder` parameter yields `createOrderMono.isbn`. The suffix is stripped
     * so the wire format matches the blocking scenarios and the JAX-RS ones, which normalize their
     * own framework-specific prefix for the same reason.
     */
    private fun FieldError.toViolation(): Violation =
        Violation("${objectName.removeSuffix(REACTIVE_OBJECT_NAME_SUFFIX)}.$field", defaultMessage ?: "")
            .invalidValue(rejectedValue)

    private fun problemResponse(
        violations: List<Violation>,
        exchange: ServerWebExchange,
    ): ResponseEntity<ValidationProblem> {
        val body = ValidationProblem(violations)
            .type(PROBLEM_TYPE)
            .title(TITLE)
            .status(HttpStatus.BAD_REQUEST.value())
            .instance(exchange.request.path.value())
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body)
    }

    companion object {
        private val PROBLEM_TYPE: URI =
            URI.create("https://lazyval.qualityminds.com/problems/validation")
        private const val TITLE = "Constraint Violation"
        private const val REACTIVE_OBJECT_NAME_SUFFIX = "Mono"
    }
}
