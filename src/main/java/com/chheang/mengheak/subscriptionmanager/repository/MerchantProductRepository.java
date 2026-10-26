package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.MerchantProduct;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantProductRepository extends JpaRepository<MerchantProduct, UUID> {
  Optional<MerchantProduct> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<MerchantProduct> findAllByTenantId(UUID tenantId, Pageable pageable);

  long countByTenantId(UUID tenantId);
}
