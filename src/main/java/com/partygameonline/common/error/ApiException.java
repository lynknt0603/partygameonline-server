package com.partygameonline.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final String clientMessage;

    public ApiException(String errorCode, HttpStatus status, String clientMessage) {
        super(clientMessage);
        this.errorCode = errorCode;
        this.status = status;
        this.clientMessage = clientMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
