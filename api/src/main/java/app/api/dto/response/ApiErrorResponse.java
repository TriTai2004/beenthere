package app.api.dto.response;

import app.api.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(boolean success, String code, String message,
        Instant timestamp, String path, Map<String, String> errors) {
    public static ApiErrorResponse of(ErrorCode code, String path) {
        return of(code, code.getMessage(), path, Map.of());
    }

    public static ApiErrorResponse of(ErrorCode code, String message, String path,
            Map<String, String> errors) {
        return new ApiErrorResponse(false, code.getCode(), message, Instant.now(), path, Map.copyOf(errors));
    }
}
