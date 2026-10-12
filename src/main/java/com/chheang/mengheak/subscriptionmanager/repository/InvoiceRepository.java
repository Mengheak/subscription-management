package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"lineItems", "subscription"})
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Invoice> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Invoice> findAllByTenantIdAndStatus(UUID tenantId, InvoiceStatus status, Pageable pageable);

    boolean existsByTenantIdAndSubscriptionIdAndPeriodStartAndStatusNot(
            UUID tenantId, UUID subscriptionId, Instant periodStart, InvoiceStatus excludedStatus);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceSequenceValue();
}
