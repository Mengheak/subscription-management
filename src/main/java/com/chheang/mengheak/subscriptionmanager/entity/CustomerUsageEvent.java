package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_usage_events")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CustomerUsageEvent extends TenantAwareEntity {
  @Column(nullable = false, updatable = false)
  private UUID subscriptionId;

  @Column(nullable = false, length = 60)
  private String metric;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 120, updatable = false)
  private String idempotencyKey;
}
