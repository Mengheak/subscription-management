package com.chheang.mengheak.subscriptionmanager.entity;

import com.chheang.mengheak.subscriptionmanager.common.constant.TenantConstants;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
@FilterDef(
        name = TenantConstants.TENANT_FILTER,
        parameters = @ParamDef(name = TenantConstants.TENANT_PARAMETER, type = UUID.class)
)
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void applyTenantOnPersist() {
        if (tenantId == null) {
            tenantId = TenantContextHolder.requireTenantId();
        }
    }
}
