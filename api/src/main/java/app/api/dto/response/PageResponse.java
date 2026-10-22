package app.api.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** A stable page representation; page zero is the first page. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last, boolean hasNext, boolean hasPrevious) {
    public PageResponse {
        content = List.copyOf(content);
    }

    /** Copies Spring Data metadata and content. Map entity pages to DTOs before calling. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
                page.hasNext(), page.hasPrevious());
    }
}
