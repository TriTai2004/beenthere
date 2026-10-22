package app.api.dto.response;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {
    @Test
    void defaultSuccessIncludesDataAndCurrentTimestamp() {
        Instant before = Instant.now();
        var response = ApiResponse.success(List.of(1, 2));
        Instant after = Instant.now();
        assertTrue(response.success());
        assertEquals("Operation completed successfully", response.message());
        assertEquals(List.of(1, 2), response.data());
        assertFalse(response.timestamp().isBefore(before));
        assertFalse(response.timestamp().isAfter(after));
    }

    @Test
    void customMessageSupportsStringData() {
        var response = ApiResponse.success("Value retrieved successfully", "value");
        assertTrue(response.success());
        assertEquals("Value retrieved successfully", response.message());
        assertEquals("value", response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void messageOnlySuccessHasNoData() {
        ApiResponse<Void> response = ApiResponse.success("Operation completed successfully");
        assertTrue(response.success());
        assertEquals("Operation completed successfully", response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }
}
