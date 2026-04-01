package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.SellerApplicationStatus;
import com.devticket.member.presentation.domain.model.SellerApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    Optional<SellerApplication> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SellerApplication> findBySellerApplicationId(UUID sellerApplicationId);

    boolean existsByUserIdAndStatus(Long userId, SellerApplicationStatus status);
}
