package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.invoice.InvoiceLineItemResponse;
import com.chheang.mengheak.subscriptionmanager.dto.invoice.InvoiceResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import com.chheang.mengheak.subscriptionmanager.entity.InvoiceLineItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface InvoiceMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "amountDue", expression = "java(invoice.amountDue())")
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "amountDue", expression = "java(invoice.amountDue())")
    @Mapping(target = "lineItems", ignore = true)
    InvoiceResponse toSummaryResponse(Invoice invoice);

    InvoiceLineItemResponse toLineItemResponse(InvoiceLineItem lineItem);
}
