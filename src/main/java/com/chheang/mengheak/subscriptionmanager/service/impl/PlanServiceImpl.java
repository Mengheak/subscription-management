package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.dto.plan.PlanResponse;
import com.chheang.mengheak.subscriptionmanager.mapper.PlanMapper;
import com.chheang.mengheak.subscriptionmanager.repository.PlanRepository;
import com.chheang.mengheak.subscriptionmanager.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planMapper.toResponses(planRepository.findAllActiveWithRates());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponse getByCode(String code) {
        return planRepository.findWithRatesByCode(code)
                .map(planMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", code));
    }
}
