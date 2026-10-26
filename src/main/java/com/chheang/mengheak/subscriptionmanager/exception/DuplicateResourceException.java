package com.chheang.mengheak.subscriptionmanager.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApplicationException {

    public DuplicateResourceException(String resource, Object identifier) {
        super(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS",
                "%s '%s' already exists".formatted(resource, identifier));
    }
}
