package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CancelSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.ChangePlanRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CreateSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.SubscriptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionService {

    SubscriptionResponse create(CreateSubscriptionRequest request);

    SubscriptionResponse changePlan(UUID subscriptionId, ChangePlanRequest request);

    SubscriptionResponse cancel(UUID subscriptionId, CancelSubscriptionRequest request);

    SubscriptionResponse get(UUID subscriptionId);

    SubscriptionResponse current();

    PageResponse<SubscriptionResponse> list(Pageable pageable);
}
