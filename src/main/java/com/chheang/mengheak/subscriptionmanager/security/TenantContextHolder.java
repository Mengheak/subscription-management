package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.exception.TenantContextMissingException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TenantContextHolder {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantContextMissingException();
        }
        return tenantId;
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static <T> T runAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT_TENANT.get();
        try {
            CURRENT_TENANT.set(tenantId);
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        }
    }
}
