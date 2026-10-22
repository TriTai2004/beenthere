package app.api.dto.response;

import app.api.user.entity.User;
import app.api.user.mapper.UserMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {
    @ParameterizedTest
    @CsvSource({
            "0, 5, 2, 3, true, false, true, false",
            "1, 5, 2, 3, false, false, true, true",
            "2, 5, 1, 3, false, true, false, true",
            "0, 0, 0, 0, true, true, false, false",
            "0, 1, 1, 1, true, true, false, false",
            "3, 5, 0, 3, false, true, false, true"
    })
    void preservesPageMetadata(int number, long total, int count, int totalPages,
            boolean first, boolean last, boolean next, boolean previous) {
        var content = List.of("one", "two").subList(0, count);
        var response = PageResponse.from(new PageImpl<>(content, PageRequest.of(number, 2), total));
        assertEquals(content, response.content());
        assertEquals(number, response.page());
        assertEquals(2, response.size());
        assertEquals(total, response.totalElements());
        assertEquals(totalPages, response.totalPages());
        assertEquals(first, response.first());
        assertEquals(last, response.last());
        assertEquals(next, response.hasNext());
        assertEquals(previous, response.hasPrevious());
    }

    @Test
    void supportsEntityToDtoMapping() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("tri");
        var entities = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        var response = PageResponse.from(entities.map(new UserMapper()::toResponse));
        assertEquals(user.getId(), response.content().getFirst().id());
        assertEquals("tri", response.content().getFirst().username());
        assertEquals(1, response.totalElements());
    }

    @Test
    void contentIsAnImmutableSnapshotEvenForDirectConstruction() {
        var source = new ArrayList<>(List.of("one"));
        var response = new PageResponse<>(source, 0, 10, 1, 1, true, true, false, false);
        source.add("two");
        assertEquals(List.of("one"), response.content());
        assertThrows(UnsupportedOperationException.class, () -> response.content().add("three"));
        var fromPage = PageResponse.from(new PageImpl<>(List.of("one")));
        assertThrows(UnsupportedOperationException.class, () -> fromPage.content().clear());
    }
}
