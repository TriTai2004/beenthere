package app.api.dto.response;

import java.time.Instant;

public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {
    /** Creates a success envelope with the default message and a UTC timestamp. */
    public static <T> ApiResponse<T> success(T data) {
        return success("Operation completed successfully", data);
    }

    /** Creates a success envelope containing DTO data and a client-facing message. */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    /** Creates a response without data. For a String payload, use the two-argument factory. */
    public static ApiResponse<Void> success(String message) {
        return success(message, null);
    }
}
