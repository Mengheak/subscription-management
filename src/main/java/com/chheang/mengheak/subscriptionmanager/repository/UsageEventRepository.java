package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.UsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Page<UsageEvent> findAllByTenantIdAndSubscriptionIdOrderByOccurredAtDesc(
            UUID tenantId, UUID subscriptionId, Pageable pageable);
}
