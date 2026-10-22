package app.api.user.service.impl;

import app.api.user.dto.CreateUserRequest;
import app.api.exception.ErrorCode;
import app.api.user.dto.UpdateUserRequest;
import app.api.user.dto.UserResponse;
import app.api.user.entity.User;
import app.api.user.exception.UserConflictException;
import app.api.user.exception.UserNotFoundException;
import app.api.user.mapper.UserMapper;
import app.api.user.repository.UserRepository;
import app.api.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    public UserResponse findById(UUID id) {
        return userMapper.toResponse(requireUser(id));
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        User user = userMapper.toEntity(request);
        checkUnique(user.getUsername(), user.getEmail(), null);
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = requireUser(id);
        String previousEmail = user.getEmail();
        userMapper.updateEntity(request, user);
        checkUnique(user.getUsername(), user.getEmail(), id);
        if (!java.util.Objects.equals(previousEmail, user.getEmail())) {
            user.setEmailVerifiedAt(null);
        }
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        userRepository.delete(requireUser(id));
        userRepository.flush();
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void checkUnique(String username, String email, UUID excludedId) {
        boolean usernameExists = excludedId == null
                ? userRepository.existsByUsernameIgnoreCase(username)
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(username, excludedId);
        if (usernameExists) {
            throw new UserConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (email != null) {
            boolean emailExists = excludedId == null
                    ? userRepository.existsByEmailIgnoreCase(email)
                    : userRepository.existsByEmailIgnoreCaseAndIdNot(email, excludedId);
            if (emailExists) {
                throw new UserConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }
    }
}
