package com.stacc.backend.common.error;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();

        return response(HttpStatus.BAD_REQUEST, "Validation failed", servletRequest(request), headers, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or unreadable",
                servletRequest(request),
                headers,
                List.of());
    }

    /**
     * Handles the remaining Spring MVC request errors, such as an unknown URL (404) or an unsupported
     * HTTP method (405). They keep the status Spring chose and use the common error format.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        HttpServletRequest servletRequest = servletRequest(request);
        if (status.is5xxServerError()) {
            logUnexpectedError(exception, servletRequest);
        }
        return response(status, messageFor(status), servletRequest, headers, List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = exception.getConstraintViolations().stream()
                .map(this::toFieldError)
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();

        return response(HttpStatus.BAD_REQUEST, "Validation failed", request, HttpHeaders.EMPTY, fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        logUnexpectedError(exception, request);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request, HttpHeaders.EMPTY, List.of());
    }

    private static String messageFor(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "The requested resource was not found.";
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            return "The request method is not supported for this resource.";
        }
        return status.is5xxServerError() ? UNEXPECTED_ERROR_MESSAGE : "The request could not be processed.";
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private void logUnexpectedError(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected server error for {} {} ({})",
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName());
    }

    private FieldValidationError toFieldError(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int separator = propertyPath.lastIndexOf('.');
        String field = separator >= 0 ? propertyPath.substring(separator + 1) : propertyPath;
        return new FieldValidationError(field, violation.getMessage());
    }

    private ResponseEntity<Object> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            HttpHeaders headers,
            List<FieldValidationError> fieldErrors) {
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(status).headers(headers).body(errorResponse);
    }
}
