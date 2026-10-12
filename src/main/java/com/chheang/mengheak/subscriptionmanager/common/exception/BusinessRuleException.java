package com.chheang.mengheak.subscriptionmanager.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApplicationException {

    public BusinessRuleException(String message) {
        super(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
