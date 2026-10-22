package app.api.user.controller;

import app.api.user.dto.CreateUserRequest;
import app.api.user.dto.UpdateUserRequest;
import app.api.dto.response.ApiResponse;
import app.api.dto.response.PageResponse;
import app.api.exception.BusinessException;
import app.api.exception.ErrorCode;
import app.api.user.dto.UserResponse;
import app.api.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "username", "displayName", "createdAt", "updatedAt");
    private final UserService userService;
    private final int maxPageSize;

    public UserController(UserService userService,
            @Value("${spring.data.web.pageable.max-page-size:100}") int maxPageSize) {
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("Maximum page size must be positive");
        }
        this.userService = userService;
        this.maxPageSize = maxPageSize;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort) {
        if (size > maxPageSize) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (sort.stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported sort field");
        }
        // A unique tie-breaker keeps page ordering stable when sort values are equal.
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        var users = userService.findAll(PageRequest.of(page, size, sort));
        return ApiResponse.success("Users retrieved successfully", PageResponse.from(users));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> findById(@PathVariable UUID id) {
        return ApiResponse.success("User retrieved successfully", userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id()))
                .body(ApiResponse.success("User created successfully", response));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated successfully", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
