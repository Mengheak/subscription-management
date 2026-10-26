package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_invoices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CustomerInvoice extends TenantAwareEntity {
  @Column(nullable = false, updatable = false)
  private UUID subscriptionId;

  @Column(nullable = false, length = 80)
  private String invoiceNumber;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 10)
  private String kind;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal baseAmount;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal usageAmount;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal total;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amountPaid;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Instant periodStart;

  @Column(nullable = false)
  private Instant periodEnd;

  @Column(nullable = false)
  private Instant dueAt;

  private Instant paidAt;
}
