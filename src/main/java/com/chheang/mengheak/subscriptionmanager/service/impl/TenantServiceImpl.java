package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.dto.tenant.TenantResponse;
import com.chheang.mengheak.subscriptionmanager.mapper.TenantMapper;
import com.chheang.mengheak.subscriptionmanager.repository.TenantRepository;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantResponse currentTenant() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return tenantRepository.findById(tenantId)
                .map(tenantMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }
}
