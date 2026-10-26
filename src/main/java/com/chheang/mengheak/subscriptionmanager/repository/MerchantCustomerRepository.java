package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.MerchantCustomer;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCustomerRepository extends JpaRepository<MerchantCustomer, UUID> {
  Optional<MerchantCustomer> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<MerchantCustomer> findByTenantIdAndExternalId(UUID tenantId, String externalId);

  Page<MerchantCustomer> findAllByTenantId(UUID tenantId, Pageable pageable);

  long countByTenantId(UUID tenantId);
}
