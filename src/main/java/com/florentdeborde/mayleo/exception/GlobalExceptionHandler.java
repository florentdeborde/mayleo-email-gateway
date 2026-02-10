package com.florentdeborde.mayleo.exception;

import com.florentdeborde.mayleo.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MayleoException.class)
    public ResponseEntity<ErrorResponse> handleMayleoException(MayleoException ex) {
        ExceptionCode exceptionCode = ex.getExceptionCode();
        HttpStatus status = switch (exceptionCode) {
            case EMAIL_CONFIG_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DAILY_QUOTA_EXCEEDED, RPM_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status)
                .body(new ErrorResponse(exceptionCode.name(), exceptionCode.getDefaultMessage()));
    }
}
