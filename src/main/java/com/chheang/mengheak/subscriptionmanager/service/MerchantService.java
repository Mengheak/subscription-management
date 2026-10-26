package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface MerchantService {
  Onboarding onboarding();

  PageResponse<ProductResponse> products(Pageable page);

  ProductResponse product(UUID id);

  ProductResponse createProduct(ProductRequest request);

  ProductResponse archiveProduct(UUID id);

  PageResponse<PriceResponse> prices(Pageable page);

  PriceResponse createPrice(PriceRequest request);

  PriceResponse archivePrice(UUID id);

  PageResponse<CustomerResponse> customers(Pageable page);

  CustomerResponse customer(UUID id);

  CustomerResponse customerByExternalId(String externalId);

  CustomerResponse createCustomer(CustomerRequest request);

  CustomerResponse updateCustomer(UUID id, CustomerUpdate request);

  PageResponse<SubscriptionResponse> subscriptions(UUID customerId, Pageable page);

  SubscriptionResponse subscription(UUID id);

  SubscriptionResponse subscribe(SubscriptionRequest request);

  SubscriptionResponse cancel(UUID id, CancelRequest request);

  AccessResponse access(String externalId, UUID productId, String feature);

  UsageAccepted recordUsage(UsageRequest request);

  UsageResponse usage(UUID subscriptionId);

  PageResponse<InvoiceResponse> invoices(UUID customerId, Pageable page);

  InvoiceResponse invoice(UUID id);

  InvoiceResponse markPaid(UUID id);

  void advance(UUID tenant, UUID subscriptionId);
}
