package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    @EntityGraph(attributePaths = "meteredRates")
    Optional<Plan> findWithRatesByCode(String code);

    @EntityGraph(attributePaths = "meteredRates")
    Optional<Plan> findWithRatesById(UUID id);

    @EntityGraph(attributePaths = "meteredRates")
    @Query("select distinct p from Plan p where p.active = true order by p.basePrice asc")
    List<Plan> findAllActiveWithRates();

    boolean existsByCode(String code);
}
