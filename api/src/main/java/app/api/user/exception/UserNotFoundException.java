package app.api.user.exception;

import java.util.UUID;
import app.api.exception.BusinessException;
import app.api.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException(UUID id) {
        super(ErrorCode.USER_NOT_FOUND, "User not found: " + id);
    }
}
