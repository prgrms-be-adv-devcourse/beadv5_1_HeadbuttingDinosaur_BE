package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.FeePolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeePolicyRepository extends JpaRepository<FeePolicy, Long> {
    Optional<FeePolicy> findTopByOrderByCreatedAtDesc();
}
