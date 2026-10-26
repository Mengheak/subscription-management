package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.MerchantPrice;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPriceRepository extends JpaRepository<MerchantPrice, UUID> {
  Optional<MerchantPrice> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<MerchantPrice> findAllByTenantId(UUID tenantId, Pageable pageable);

  long countByTenantIdAndActiveTrue(UUID tenantId);
}
