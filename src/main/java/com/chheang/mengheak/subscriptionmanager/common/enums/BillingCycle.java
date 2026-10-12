package com.chheang.mengheak.subscriptionmanager.common.enums;

import java.time.Instant;
import java.time.ZoneOffset;

public enum BillingCycle {

    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }

    public Instant advance(Instant from) {
        return from.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    public Instant retreat(Instant from) {
        return from.atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
    }
}
