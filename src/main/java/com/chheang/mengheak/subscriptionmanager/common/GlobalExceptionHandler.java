package com.chheang.mengheak.subscriptionmanager.common;

import com.chheang.mengheak.subscriptionmanager.exception.ApplicationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleApplication(ApplicationException ex, HttpServletRequest request) {
        log.debug("Application exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ResponseEntity<ApiResponse<Map<String, Object>>> problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid", request);
        problem.getBody().data().put("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            violations.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ResponseEntity<ApiResponse<Map<String, Object>>> problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more parameters are invalid", request);
        problem.getBody().data().put("fieldErrors", violations);
        return problem;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            PropertyReferenceException.class
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.debug("Malformed request: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request could not be read; check the payload and parameter types", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "No endpoint %s %s".formatted(request.getMethod(), request.getRequestURI()), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                  HttpServletRequest request) {
        ResponseEntity<ApiResponse<Map<String, Object>>> problem = problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "%s is not supported by this endpoint".formatted(ex.getMethod()), request);
        problem.getBody().data().put("supportedMethods", ex.getSupportedMethods());
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                     HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content type %s is not supported; use application/json".formatted(ex.getContentType()), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("Authentication failure: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Authentication is required or the supplied credentials are invalid", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently; retry the request", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with the current state of the data", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [errorId={}] on {} {}", errorId,
                request.getMethod(), request.getRequestURI(), ex);

        ResponseEntity<ApiResponse<Map<String, Object>>> problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred; quote the errorId when contacting support", request);
        problem.getBody().data().put("errorId", errorId);
        return problem;
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> problem(
            HttpStatus status, String errorCode, String detail, HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", errorCode);
        data.put("path", request.getRequestURI());
        data.put("timestamp", Instant.now());
        return ResponseEntity.status(status).body(ApiResponse.error(detail, data));
    }
}
