package com.chheang.mengheak.subscriptionmanager.common.enums;

public enum InvoiceStatus {

    DRAFT,
    OPEN,
    PAID,
    VOID,
    UNCOLLECTIBLE;

    public boolean isTerminal() {
        return this == PAID || this == VOID || this == UNCOLLECTIBLE;
    }
}
