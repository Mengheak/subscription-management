package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.constant.TenantConstants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@Order(TenantFilterAspect.ORDER)
public class TenantFilterAspect {

    public static final int ORDER = 100;

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.chheang.mengheak.subscriptionmanager.service..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (TenantContextHolder.isSet() && TransactionSynchronizationManager.isActualTransactionActive()) {
            UUID tenantId = TenantContextHolder.requireTenantId();
            Session session = entityManager.unwrap(Session.class);

            Filter filter = session.enableFilter(TenantConstants.TENANT_FILTER);
            filter.setParameter(TenantConstants.TENANT_PARAMETER, tenantId);

            log.trace("Tenant filter enabled for {} on {}", tenantId, joinPoint.getSignature().toShortString());
        }
        return joinPoint.proceed();
    }
}
