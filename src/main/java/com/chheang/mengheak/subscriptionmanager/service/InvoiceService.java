package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.invoice.InvoiceResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InvoiceService {

    InvoiceResponse generateForCurrentPeriod(UUID subscriptionId);

    InvoiceResponse get(UUID invoiceId);

    PageResponse<InvoiceResponse> list(InvoiceStatus status, Pageable pageable);

    InvoiceResponse markPaid(UUID invoiceId);
}
