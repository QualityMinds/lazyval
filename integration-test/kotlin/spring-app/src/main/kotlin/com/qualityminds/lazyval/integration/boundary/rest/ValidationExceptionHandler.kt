package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.ValidationProblem
import com.qualityminds.lazyval.integration.boundary.rest.model.Violation
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ValidationProblem> {
        val violations = ex.bindingResult.fieldErrors.map { it.toViolation() }
        return problemResponse(violations, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ValidationProblem> {
        val violations = ex.constraintViolations.map {
            Violation(
                field = it.propertyPath.toString(),
                message = it.message,
                invalidValue = it.invalidValue,
            )
        }
        return problemResponse(violations, request)
    }

    /**
     * Maps body-deserialization failures. Domain primitives (Isbn, Quantity) self-validate in
     * their constructors and throw IllegalArgumentException, which Jackson surfaces as a
     * deserialization error before bean validation gets a chance to run on the request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ValidationProblem> {
        val violation = Violation(field = "body", message = ex.mostSpecificCause.message ?: "")
        return problemResponse(listOf(violation), request)
    }

    private fun FieldError.toViolation(): Violation = Violation(
        field = "$objectName.$field",
        message = defaultMessage ?: "",
        invalidValue = rejectedValue,
    )

    private fun problemResponse(
        violations: List<Violation>,
        request: HttpServletRequest,
    ): ResponseEntity<ValidationProblem> {
        val body = ValidationProblem(
            violations = violations,
            type = PROBLEM_TYPE,
            title = TITLE,
            status = HttpStatus.BAD_REQUEST.value(),
            instance = request.requestURI,
        )
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body)
    }

    companion object {
        private val PROBLEM_TYPE: URI =
            URI.create("https://lazyval.qualityminds.com/problems/validation")
        private const val TITLE = "Constraint Violation"
    }
}
