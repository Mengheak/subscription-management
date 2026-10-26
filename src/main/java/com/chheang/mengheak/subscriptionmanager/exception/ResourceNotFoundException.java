package com.chheang.mengheak.subscriptionmanager.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "%s '%s' was not found".formatted(resource, identifier));
    }
}
