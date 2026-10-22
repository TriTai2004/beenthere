package app.api.exception;

import app.api.dto.response.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException exception, WebRequest request) {
        return response(exception.getErrorCode(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> constraintViolation(ConstraintViolationException exception, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), errors, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> dataConflict(DataIntegrityViolationException exception, WebRequest request) {
        return response(ErrorCode.RESOURCE_CONFLICT, ErrorCode.RESOURCE_CONFLICT.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> unauthorized(AuthenticationException exception, WebRequest request) {
        return response(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<Object> authenticationServiceFailure(InternalAuthenticationServiceException exception,
            WebRequest request) {
        log.error("Authentication service failure", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> denied(AccessDeniedException exception, WebRequest request) {
        return response(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        log.error("Unexpected request failure", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), Map.of(), request);
    }

    // The superclass owns MVC exception mappings (including malformed bodies, missing
    // parameters and type mismatches). Preserve framework statuses and headers here.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR
                : status.value() == 404 ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.INVALID_REQUEST;
        var errors = new LinkedHashMap<String, String>();
        if (exception instanceof MethodArgumentNotValidException validation) {
            code = ErrorCode.VALIDATION_ERROR;
            validation.getBindingResult().getFieldErrors().forEach(error ->
                    errors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
                            ? "Invalid value" : error.getDefaultMessage()));
            validation.getBindingResult().getGlobalErrors().forEach(error ->
                    errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage() == null
                            ? "Invalid value" : error.getDefaultMessage()));
        } else if (exception instanceof HandlerMethodValidationException && !status.is5xxServerError()) {
            code = ErrorCode.VALIDATION_ERROR;
        }
        if (status.is5xxServerError()) {
            log.error("Framework request failure", exception);
        }
        return super.handleExceptionInternal(exception,
                ApiErrorResponse.of(code, code.getMessage(), path(request), errors), headers, status, request);
    }

    private ResponseEntity<Object> response(ErrorCode code, String message,
            Map<String, String> errors, WebRequest request) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiErrorResponse.of(code, message, path(request), errors));
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
