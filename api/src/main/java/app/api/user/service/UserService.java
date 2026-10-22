package app.api.user.service;

import app.api.user.dto.CreateUserRequest;
import app.api.user.dto.UpdateUserRequest;
import app.api.user.dto.UserResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    Page<UserResponse> findAll(Pageable pageable);
    UserResponse findById(UUID id);
    UserResponse create(CreateUserRequest request);
    UserResponse update(UUID id, UpdateUserRequest request);
    void delete(UUID id);
}
