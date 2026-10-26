package com.chheang.mengheak.subscriptionmanager.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant_api_keys")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MerchantApiKey extends TenantAwareEntity {
  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 64, updatable = false)
  private String tokenHash;

  @Column(nullable = false, length = 24, updatable = false)
  private String prefix;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Set<String> scopes;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;
}
