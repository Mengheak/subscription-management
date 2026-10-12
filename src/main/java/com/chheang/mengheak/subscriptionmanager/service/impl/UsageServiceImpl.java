package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.enums.SubscriptionStatus;
import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import com.chheang.mengheak.subscriptionmanager.common.exception.BusinessRuleException;
import com.chheang.mengheak.subscriptionmanager.common.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.config.properties.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.dto.usage.RecordUsageRequest;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageAcceptedResponse;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageMetricSummary;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageSummaryResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.PlanMeteredRate;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import com.chheang.mengheak.subscriptionmanager.mapper.UsageEventMapper;
import com.chheang.mengheak.subscriptionmanager.messaging.UsageEventProducer;
import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import com.chheang.mengheak.subscriptionmanager.repository.SubscriptionRepository;
import com.chheang.mengheak.subscriptionmanager.repository.UsageCounterRepository;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final SubscriptionRepository subscriptionRepository;
    private final UsageCounterRepository usageCounterRepository;
    private final UsageEventProducer usageEventProducer;
    private final UsageEventMapper usageEventMapper;
    private final BillingProperties billingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UsageAcceptedResponse record(RecordUsageRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Subscription subscription = subscriptionRepository
                .findFirstByTenantIdAndStatusIn(tenantId, SubscriptionStatus.liveStatuses())
                .orElseThrow(() -> new BusinessRuleException("NO_LIVE_SUBSCRIPTION",
                        "This tenant has no live subscription to attribute usage to"));

        Instant now = clock.instant();
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : now;
        if (occurredAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new BusinessRuleException("USAGE_TIMESTAMP_IN_FUTURE",
                    "occurredAt may not be more than %d minutes in the future".formatted(MAX_CLOCK_SKEW.toMinutes()));
        }

        String idempotencyKey = (request.idempotencyKey() == null || request.idempotencyKey().isBlank())
                ? UUID.randomUUID().toString()
                : request.idempotencyKey();

        UsageEventMessage message = usageEventMapper.toMessage(
                request, tenantId, subscription.getId(), occurredAt, idempotencyKey);

        SendResult<String, UsageEventMessage> result = usageEventProducer.publish(message);
        RecordMetadata metadata = result.getRecordMetadata();

        return usageEventMapper.toAcceptedResponse(
                message, metadata.topic(), metadata.partition(), metadata.offset());
    }

    @Override
    @Transactional(readOnly = true)
    public UsageSummaryResponse currentPeriodSummary(UUID subscriptionId) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Subscription subscription = subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        List<UsageCounter> counters = usageCounterRepository.findAllByTenantIdAndSubscriptionIdAndPeriodStart(
                tenantId, subscriptionId, subscription.getCurrentPeriodStart());

        Map<UsageMetric, BigDecimal> consumed = new EnumMap<>(UsageMetric.class);
        for (UsageCounter counter : counters) {
            consumed.merge(counter.getMetric(), counter.getTotalQuantity(), BigDecimal::add);
        }

        Plan plan = subscription.getPlan();
        List<UsageMetricSummary> metrics = new ArrayList<>();
        BigDecimal estimatedTotal = BigDecimal.ZERO;

        for (PlanMeteredRate rate : plan.getMeteredRates()) {
            BigDecimal total = consumed.getOrDefault(rate.getMetric(), BigDecimal.ZERO);
            BigDecimal overage = total.subtract(rate.getIncludedQuantity()).max(BigDecimal.ZERO);
            BigDecimal cost = billingProperties.toMoney(overage.multiply(rate.getUnitPrice()));

            metrics.add(new UsageMetricSummary(
                    rate.getMetric(),
                    billingProperties.toQuantity(total),
                    rate.getIncludedQuantity(),
                    billingProperties.toQuantity(overage),
                    rate.getUnitPrice(),
                    cost));

            estimatedTotal = estimatedTotal.add(cost);
        }

        return new UsageSummaryResponse(
                subscriptionId,
                plan.getCode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                plan.getCurrency(),
                metrics,
                billingProperties.toMoney(estimatedTotal));
    }
}
