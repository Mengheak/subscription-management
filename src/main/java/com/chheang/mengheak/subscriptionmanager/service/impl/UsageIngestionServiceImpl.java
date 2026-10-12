package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.config.properties.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import com.chheang.mengheak.subscriptionmanager.entity.UsageEvent;
import com.chheang.mengheak.subscriptionmanager.messaging.UsageEventConsumer.NonRetryableUsageEventException;
import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import com.chheang.mengheak.subscriptionmanager.repository.SubscriptionRepository;
import com.chheang.mengheak.subscriptionmanager.repository.UsageCounterRepository;
import com.chheang.mengheak.subscriptionmanager.repository.UsageEventRepository;
import com.chheang.mengheak.subscriptionmanager.service.UsageIngestionService;
import com.chheang.mengheak.subscriptionmanager.service.billing.BillingPeriodResolver;
import com.chheang.mengheak.subscriptionmanager.service.billing.BillingPeriodResolver.BillingPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageIngestionServiceImpl implements UsageIngestionService {

    private final UsageEventRepository usageEventRepository;
    private final UsageCounterRepository usageCounterRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingPeriodResolver billingPeriodResolver;
    private final BillingProperties billingProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void ingest(UsageEventMessage message) {
        UUID tenantId = message.tenantId();

        if (usageEventRepository.existsByTenantIdAndIdempotencyKey(tenantId, message.idempotencyKey())) {
            log.debug("Usage event {} for tenant {} already applied; ignoring redelivery",
                    message.idempotencyKey(), tenantId);
            return;
        }

        Subscription subscription = subscriptionRepository
                .findByIdAndTenantId(message.subscriptionId(), tenantId)
                .orElseThrow(() -> new NonRetryableUsageEventException(
                        "Usage event %s references subscription %s which does not belong to tenant %s"
                                .formatted(message.idempotencyKey(), message.subscriptionId(), tenantId)));

        BigDecimal quantity = billingProperties.toQuantity(message.quantity());
        BillingPeriod period = billingPeriodResolver.resolve(subscription, message.occurredAt());

        if (!period.contains(message.occurredAt())) {
            log.warn("Usage event {} occurred at {} outside the resolved period {}..{}",
                    message.idempotencyKey(), message.occurredAt(), period.start(), period.end());
        }

        usageEventRepository.save(UsageEvent.builder()
                .tenantId(tenantId)
                .subscription(subscription)
                .metric(message.metric())
                .quantity(quantity)
                .idempotencyKey(message.idempotencyKey())
                .source(message.source())
                .occurredAt(message.occurredAt())
                .recordedAt(clock.instant())
                .build());

        incrementCounter(subscription, message, quantity, period);

        log.debug("Applied {} {} to subscription {} for period starting {}",
                quantity, message.metric(), subscription.getId(), period.start());
    }

    private void incrementCounter(Subscription subscription,
                                  UsageEventMessage message,
                                  BigDecimal quantity,
                                  BillingPeriod period) {

        UsageCounter counter = usageCounterRepository
                .findByTenantIdAndSubscriptionIdAndMetricAndPeriodStart(
                        message.tenantId(), subscription.getId(), message.metric(), period.start())
                .orElseGet(() -> usageCounterRepository.saveAndFlush(UsageCounter.builder()
                        .tenantId(message.tenantId())
                        .subscription(subscription)
                        .metric(message.metric())
                        .periodStart(period.start())
                        .periodEnd(period.end())
                        .totalQuantity(BigDecimal.ZERO)
                        .build()));

        counter.increment(quantity);
    }
}
