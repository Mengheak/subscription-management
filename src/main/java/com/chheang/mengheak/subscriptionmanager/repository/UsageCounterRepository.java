package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageCounterRepository extends JpaRepository<UsageCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UsageCounter> findByTenantIdAndSubscriptionIdAndMetricAndPeriodStart(
            UUID tenantId, UUID subscriptionId, UsageMetric metric, Instant periodStart);

    List<UsageCounter> findAllByTenantIdAndSubscriptionIdAndPeriodStart(
            UUID tenantId, UUID subscriptionId, Instant periodStart);
}
