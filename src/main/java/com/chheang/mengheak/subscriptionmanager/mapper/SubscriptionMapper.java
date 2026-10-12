package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.subscription.SubscriptionResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface SubscriptionMapper {

    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "planCode", source = "plan.code")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "billingCycle", source = "plan.billingCycle")
    @Mapping(target = "basePrice", source = "plan.basePrice")
    @Mapping(target = "currency", source = "plan.currency")
    SubscriptionResponse toResponse(Subscription subscription);
}
