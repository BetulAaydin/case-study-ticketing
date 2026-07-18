package com.turkcell.mayacore.commonlibrary.exception;

public class SystemException extends RuntimeException {

    private final String errorCode;

    public SystemException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
