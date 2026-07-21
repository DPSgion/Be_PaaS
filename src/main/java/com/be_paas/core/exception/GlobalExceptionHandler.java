package com.be_paas.core.exception;

import com.be_paas.core.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Handle Resource Not Found (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage()));
    }

    // 2. Handle Business Rule Violations (e.g., incorrect password, locked account)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(HttpStatusCode.valueOf(ex.getStatus()))
                .body(ErrorResponse.of(ex.getStatus(), "Business Rule Violation", ex.getMessage()));
    }

    // 3. Handle Validation Errors for DTOs (422)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatusCode.valueOf(422))
                .body(ErrorResponse.ofValidation(
                        422,
                        "Validation Failed",
                        "One or more fields failed validation",
                        fieldErrors));
    }

    // 4. Handle Validation Errors for Parameters (422)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(cv -> fieldErrors.put(cv.getPropertyPath().toString(), cv.getMessage()));
        return ResponseEntity.status(HttpStatusCode.valueOf(422))
                .body(ErrorResponse.ofValidation(
                        422,
                        "Validation Failed",
                        "One or more parameters failed validation",
                        fieldErrors));
    }

    // 5. Handle Database Constraint Violations (e.g., duplicate username/email) (409)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("[Data constraint violation] - {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "Data Conflict",
                        "Data already exists or violates system constraints."));
    }

    // 6. Handle File Upload Size Exceeded (413)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.warn("[File size exceeded] - Uploaded file is too large!");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(
                        413,
                        "Payload Too Large",
                        "File size exceeds the allowed limit. Please select a smaller file."));
    }

    // 7. Handle Uncaught Exceptions (Fallback - 500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("[Unhandled exception]", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal Server Error",
                        "An unexpected error occurred."));
    }

    // 8. Handle Authentication Errors (401)
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("[Authentication failed] - {}", ex.getMessage());

        String message = ex instanceof BadCredentialsException
                ? "Incorrect username or password !"
                : "Authentication failed !";

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        401,
                        "Unauthorized",
                        message
                ));
    }

    // 8. Handle Violate Admin Permission (403)
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403,
                "error", "Forbidden",
                "message", "You don't have permission to do this action ! Just ADMIN can do that.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 9. Handle SSE Disconnected Client (Broken Pipe)
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex) {
        log.info("[SSE Client Disconnected]");
    }

}
