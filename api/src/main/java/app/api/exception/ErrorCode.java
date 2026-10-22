package app.api.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("Resource not found", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR("Validation failed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("Authentication is required", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Invalid email or password", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Access denied", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("Username is already in use", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("Email is already in use", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("Business rule violated", HttpStatus.BAD_REQUEST),
    INVALID_OPERATION("Operation is not allowed", HttpStatus.BAD_REQUEST),
    RESOURCE_CONFLICT("Resource conflicts with existing data or related records", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String getCode() { return name(); }
    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}
