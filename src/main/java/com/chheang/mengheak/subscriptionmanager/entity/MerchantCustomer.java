package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "merchant_customers")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MerchantCustomer extends TenantAwareEntity {
  @Column(nullable = false, length = 120, updatable = false)
  private String externalId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(nullable = false)
  private boolean active;
}
