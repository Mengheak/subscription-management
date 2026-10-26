package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.CustomerUsageEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CustomerUsageEventRepository extends JpaRepository<CustomerUsageEvent, UUID> {
  Optional<CustomerUsageEvent> findByTenantIdAndIdempotencyKey(UUID tenantId, String key);

  @Query(
      "select coalesce(sum(e.quantity),0) from CustomerUsageEvent e where e.tenantId=:tenant and"
          + " e.subscriptionId=:subscription and e.metric=:metric and e.occurredAt>=:start and"
          + " e.occurredAt<:end")
  BigDecimal total(UUID tenant, UUID subscription, String metric, Instant start, Instant end);
}
