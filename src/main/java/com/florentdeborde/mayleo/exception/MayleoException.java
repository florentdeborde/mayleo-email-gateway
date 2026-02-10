package com.florentdeborde.mayleo.exception;

import lombok.Getter;

@Getter
public class MayleoException extends RuntimeException {

    private final ExceptionCode exceptionCode;

    public MayleoException(ExceptionCode exceptionCode) {
        super(exceptionCode.getDefaultMessage());
        this.exceptionCode = exceptionCode;
    }
}

