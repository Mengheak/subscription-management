package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.common.enums.SubscriptionStatus;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = {"plan", "plan.meteredRates"})
    Optional<Subscription> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = "plan")
    Page<Subscription> findAllByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "plan")
    Optional<Subscription> findFirstByTenantIdAndStatusIn(UUID tenantId, Collection<SubscriptionStatus> statuses);

    boolean existsByTenantIdAndStatusIn(UUID tenantId, Collection<SubscriptionStatus> statuses);

    @EntityGraph(attributePaths = {"plan", "plan.meteredRates"})
    List<Subscription> findAllByStatusInAndCurrentPeriodEndLessThanEqual(
            Collection<SubscriptionStatus> statuses, Instant cutoff);
}
