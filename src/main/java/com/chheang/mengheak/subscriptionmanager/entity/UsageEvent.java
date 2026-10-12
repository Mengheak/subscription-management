package com.chheang.mengheak.subscriptionmanager.entity;

import com.chheang.mengheak.subscriptionmanager.common.constant.TenantConstants;
import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "usage_events")
@Filter(name = TenantConstants.TENANT_FILTER, condition = TenantConstants.TENANT_CONDITION)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "subscription")
public class UsageEvent extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private UsageMetric metric;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
