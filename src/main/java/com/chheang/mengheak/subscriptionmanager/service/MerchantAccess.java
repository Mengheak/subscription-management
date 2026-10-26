package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.enums.*;
import com.chheang.mengheak.subscriptionmanager.exception.BusinessRuleException;
import com.chheang.mengheak.subscriptionmanager.repository.*;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MerchantAccess {
  private final SubscriptionRepository subscriptions;
  private final TenantRepository tenants;
  private final Clock clock;

  public boolean unlocked(UUID tenant) {
    return tenants.findById(tenant).filter(t -> t.isActive()).isPresent()
        && subscriptions
            .findFirstByTenantIdAndStatusIn(tenant, SubscriptionStatus.liveStatuses())
            .filter(
                s ->
                    s.getStatus() == SubscriptionStatus.ACTIVE
                        || s.getStatus() == SubscriptionStatus.TRIALING)
            .filter(s -> s.getCurrentPeriodEnd().isAfter(clock.instant()))
            .isPresent();
  }

  public UUID require() {
    UUID tenant = TenantContextHolder.requireTenantId();
    if (!unlocked(tenant))
      throw new BusinessRuleException(
          "PLATFORM_SUBSCRIPTION_REQUIRED",
          "An active or trialing platform subscription is required. Open Platform plans to activate"
              + " your workspace.");
    return tenant;
  }

  /**
   * Serializes merchant commands, including duplicate delivery and renewal, across app instances.
   */
  public UUID lock() {
    UUID tenant = TenantContextHolder.requireTenantId();
    tenants
        .findLockedById(tenant)
        .orElseThrow(() -> new BusinessRuleException("Workspace is unavailable"));
    return require();
  }
}
