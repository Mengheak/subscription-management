package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CustomerSubscription extends TenantAwareEntity {
  @Column(nullable = false, updatable = false)
  private UUID customerId;

  @Column(nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private UUID priceId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Instant periodStart;

  @Column(nullable = false)
  private Instant periodEnd;

  @Column(nullable = false)
  private boolean cancelAtPeriodEnd;

  @Column(nullable = false, updatable = false)
  private boolean skipTrial;

  @Column(nullable = false, length = 120, updatable = false)
  private String idempotencyKey;
}
