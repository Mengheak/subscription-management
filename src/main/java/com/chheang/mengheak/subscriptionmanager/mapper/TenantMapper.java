package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.tenant.TenantResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);
}
