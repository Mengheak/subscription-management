package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.exception.BusinessRuleException;
import com.chheang.mengheak.subscriptionmanager.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.invoice.InvoiceResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import com.chheang.mengheak.subscriptionmanager.mapper.InvoiceMapper;
import com.chheang.mengheak.subscriptionmanager.repository.InvoiceRepository;
import com.chheang.mengheak.subscriptionmanager.repository.SubscriptionRepository;
import com.chheang.mengheak.subscriptionmanager.repository.UsageCounterRepository;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.InvoiceService;
import com.chheang.mengheak.subscriptionmanager.service.billing.InvoiceAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageCounterRepository usageCounterRepository;
    private final InvoiceAssembler invoiceAssembler;
    private final InvoiceMapper invoiceMapper;
    private final Clock clock;

    @Override
    @Transactional
    public InvoiceResponse generateForCurrentPeriod(UUID subscriptionId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Subscription subscription = subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));

        if (invoiceRepository.existsByTenantIdAndSubscriptionIdAndPeriodStartAndStatusNot(
                tenantId, subscriptionId, subscription.getCurrentPeriodStart(), InvoiceStatus.VOID)) {
            throw new BusinessRuleException("PERIOD_ALREADY_INVOICED",
                    "An invoice already covers the period starting %s".formatted(subscription.getCurrentPeriodStart()));
        }

        Invoice invoice = invoiceAssembler.openInvoice(
                subscription, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());

        if (!isFullyWithinTrial(subscription)) {
            invoiceAssembler.addSubscriptionLine(invoice, subscription);
        }

        List<UsageCounter> counters = usageCounterRepository.findAllByTenantIdAndSubscriptionIdAndPeriodStart(
                tenantId, subscriptionId, subscription.getCurrentPeriodStart());
        invoiceAssembler.addUsageLines(invoice, subscription, counters);

        invoiceAssembler.finalise(invoice);
        Invoice saved = invoiceRepository.save(invoice);

        log.info("Generated invoice {} for subscription {}: subtotal={} tax={} total={} ({} lines)",
                saved.getInvoiceNumber(), subscriptionId, saved.getSubtotal(), saved.getTaxAmount(),
                saved.getTotal(), saved.getLineItems().size());

        return invoiceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID invoiceId) {
        return invoiceMapper.toResponse(requireInvoice(invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(InvoiceStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        var page = status == null
                ? invoiceRepository.findAllByTenantId(tenantId, pageable)
                : invoiceRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);

        return PageResponse.from(page, invoiceMapper::toSummaryResponse);
    }

    @Override
    @Transactional
    public InvoiceResponse markPaid(UUID invoiceId) {
        Invoice invoice = requireInvoice(invoiceId);

        if (invoice.getStatus().isTerminal()) {
            throw new BusinessRuleException("INVOICE_NOT_PAYABLE",
                    "Invoice %s is %s and cannot be paid".formatted(invoice.getInvoiceNumber(), invoice.getStatus()));
        }

        invoice.setAmountPaid(invoice.getTotal());
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(clock.instant());

        log.info("Invoice {} marked paid ({} {})",
                invoice.getInvoiceNumber(), invoice.getTotal(), invoice.getCurrency());

        return invoiceMapper.toResponse(invoice);
    }

    private boolean isFullyWithinTrial(Subscription subscription) {
        return subscription.getTrialEnd() != null
                && !subscription.getTrialEnd().isBefore(subscription.getCurrentPeriodEnd());
    }

    private Invoice requireInvoice(UUID invoiceId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    }
}
