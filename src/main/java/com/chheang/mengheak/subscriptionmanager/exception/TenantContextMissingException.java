package com.chheang.mengheak.subscriptionmanager.exception;

import org.springframework.http.HttpStatus;

public class TenantContextMissingException extends ApplicationException {

    public TenantContextMissingException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "TENANT_CONTEXT_MISSING",
                "No tenant is bound to the current execution context");
    }
}
