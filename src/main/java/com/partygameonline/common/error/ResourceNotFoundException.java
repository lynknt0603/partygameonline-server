package com.partygameonline.common.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String errorCode, String clientMessage) {
        super(errorCode, HttpStatus.NOT_FOUND, clientMessage);
    }
}
