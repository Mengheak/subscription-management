package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.plan.MeteredRateResponse;
import com.chheang.mengheak.subscriptionmanager.dto.plan.PlanResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.PlanMeteredRate;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = CentralMapperConfig.class)
public interface PlanMapper {

    PlanResponse toResponse(Plan plan);

    List<PlanResponse> toResponses(List<Plan> plans);

    MeteredRateResponse toRateResponse(PlanMeteredRate rate);
}
