package app.api.auth.exception;

import app.api.exception.BusinessException;
import app.api.exception.ErrorCode;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() { super(ErrorCode.INVALID_REFRESH_TOKEN); }
}
