package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.CustomerSubscription;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, UUID> {
  Optional<CustomerSubscription> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<CustomerSubscription> findByTenantIdAndIdempotencyKey(UUID tenantId, String key);

  Optional<CustomerSubscription> findFirstByTenantIdAndCustomerIdAndProductIdAndStatusIn(
      UUID tenantId, UUID customerId, UUID productId, Collection<String> statuses);

  Page<CustomerSubscription> findAllByTenantId(UUID tenantId, Pageable pageable);

  Page<CustomerSubscription> findAllByTenantIdAndCustomerId(
      UUID tenantId, UUID customerId, Pageable pageable);

  long countByTenantId(UUID tenantId);

  @Query(
      "select s from CustomerSubscription s where s.status in ('ACTIVE','TRIALING') and s.periodEnd"
          + " <= :at and exists (select p from Subscription p where p.tenantId=s.tenantId and"
          + " p.status in ('ACTIVE','TRIALING') and p.currentPeriodEnd>:at) and exists (select t"
          + " from Tenant t where t.id=s.tenantId and t.status='ACTIVE') order by s.periodEnd")
  List<CustomerSubscription> findDue(Instant at, Pageable pageable);
}
