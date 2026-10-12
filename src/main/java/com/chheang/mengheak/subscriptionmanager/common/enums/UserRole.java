package com.chheang.mengheak.subscriptionmanager.common.enums;

public enum UserRole {

    TENANT_ADMIN,

    TENANT_MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}
