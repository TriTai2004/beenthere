package app.api.user.exception;

import app.api.exception.BusinessException;
import app.api.exception.ErrorCode;

public class UserConflictException extends BusinessException {
    public UserConflictException(String message) {
        super(ErrorCode.USER_ALREADY_EXISTS, message);
    }

    public UserConflictException(ErrorCode code) {
        super(code);
    }
}
