package com.chheang.mengheak.subscriptionmanager.common.enums;

import java.util.EnumSet;
import java.util.Set;

public enum SubscriptionStatus {

    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED;

    private static final Set<SubscriptionStatus> LIVE = EnumSet.of(TRIALING, ACTIVE, PAST_DUE);

    public boolean isLive() {
        return LIVE.contains(this);
    }

    public static Set<SubscriptionStatus> liveStatuses() {
        return EnumSet.copyOf(LIVE);
    }
}
