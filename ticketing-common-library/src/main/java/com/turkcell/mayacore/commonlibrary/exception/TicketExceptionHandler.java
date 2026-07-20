package com.turkcell.mayacore.commonlibrary.exception;

import com.turkcell.mayacore.commonlibrary.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * CommonLibrary merkezi handler. Uygulama kodunda BusinessException / SystemException
 * icin ayri @ExceptionHandler yazilmaz.
 */
@RestControllerAdvice
public class TicketExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystem(SystemException ex) {
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}
