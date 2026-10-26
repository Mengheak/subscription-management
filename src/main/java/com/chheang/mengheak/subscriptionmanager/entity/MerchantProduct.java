package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "merchant_products")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MerchantProduct extends TenantAwareEntity {
  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private boolean active;
}
