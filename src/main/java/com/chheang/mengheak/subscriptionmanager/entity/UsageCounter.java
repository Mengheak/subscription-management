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
@Table(name = "usage_counters")
@Filter(name = TenantConstants.TENANT_FILTER, condition = TenantConstants.TENANT_CONDITION)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "subscription")
public class UsageCounter extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private UsageMetric metric;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "total_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalQuantity;

    public void increment(BigDecimal delta) {
        totalQuantity = totalQuantity.add(delta);
    }
}
