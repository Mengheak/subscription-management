package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.CustomerInvoice;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, UUID> {
  Optional<CustomerInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<CustomerInvoice> findByTenantIdAndSubscriptionIdAndPeriodStartAndKind(
      UUID tenantId, UUID subscriptionId, Instant start, String kind);

  Page<CustomerInvoice> findAllByTenantId(UUID tenantId, Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "select i from CustomerInvoice i where i.tenantId=:tenantId and i.subscriptionId in (select"
          + " s.id from CustomerSubscription s where s.tenantId=:tenantId and"
          + " s.customerId=:customerId)")
  Page<CustomerInvoice> findByCustomer(UUID tenantId, UUID customerId, Pageable pageable);

  boolean existsByTenantIdAndSubscriptionIdAndStatusAndDueAtBefore(
      UUID tenantId, UUID subscriptionId, String status, Instant now);
}
