package org.example.mercenary.global.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    protected BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.code = status.value();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
