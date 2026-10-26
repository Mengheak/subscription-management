package com.chheang.mengheak.subscriptionmanager.entity;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.MeteredRate;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant_prices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MerchantPrice extends TenantAwareEntity {
  @Column(nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BillingCycle billingCycle;

  @Column(nullable = false)
  private int trialDays;

  @Column(nullable = false)
  private boolean active;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Set<String> features;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<MeteredRate> meteredRates;
}
