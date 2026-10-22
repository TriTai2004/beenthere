package app.api.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    // Custom messages must be safe for clients; never pass database exception messages.
    public BusinessException(ErrorCode errorCode, String message) {
        super(message == null ? errorCode.getMessage() : message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
