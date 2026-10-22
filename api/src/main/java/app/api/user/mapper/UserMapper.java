package app.api.user.mapper;

import app.api.user.dto.CreateUserRequest;
import app.api.user.dto.UpdateUserRequest;
import app.api.user.dto.UserResponse;
import app.api.user.entity.User;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public User toEntity(CreateUserRequest request) {
        User user = new User();
        user.setUsername(normalizeIdentifier(request.username()));
        user.setDisplayName(request.displayName().strip());
        user.setEmail(normalizeIdentifier(request.email()));
        user.setBio(request.bio());
        user.setAvatarKey(request.avatarKey());
        user.setCoverKey(request.coverKey());
        return user;
    }

    public void updateEntity(UpdateUserRequest request, User user) {
        user.setUsername(normalizeIdentifier(request.username()));
        user.setDisplayName(request.displayName().strip());
        user.setEmail(normalizeIdentifier(request.email()));
        user.setBio(request.bio());
        user.setAvatarKey(request.avatarKey());
        user.setCoverKey(request.coverKey());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getBio(), user.getAvatarKey(), user.getCoverKey(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private String normalizeIdentifier(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
