package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.repository.CustomerSubscriptionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantLifecycleJob {
  private final CustomerSubscriptionRepository subscriptions;
  private final MerchantService service;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${app.merchant.lifecycle-delay:60000}")
  public void renew() {
    for (var s : subscriptions.findDue(clock.instant(), PageRequest.of(0, 100))) {
      try {
        service.advance(s.getTenantId(), s.getId());
      } catch (Exception e) {
        log.error("Merchant lifecycle failed for subscription {}", s.getId(), e);
      }
    }
  }
}
