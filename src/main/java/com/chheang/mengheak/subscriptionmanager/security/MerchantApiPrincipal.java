package com.chheang.mengheak.subscriptionmanager.security;

import java.util.UUID;

public record MerchantApiPrincipal(UUID keyId, UUID tenantId) {}
