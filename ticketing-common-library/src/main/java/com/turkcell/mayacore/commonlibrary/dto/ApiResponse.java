package com.turkcell.mayacore.commonlibrary.dto;

import java.time.LocalDateTime;

public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime timestamp;

    private ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> error(String errorCode, String errorMessage) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.errorCode = errorCode;
        response.errorMessage = errorMessage;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
