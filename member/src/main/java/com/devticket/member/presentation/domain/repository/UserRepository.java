package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(UUID userId);

    Optional<User> findSellerById(Long id);

    boolean existsByEmail(String email);

    Optional<User> findByProviderTypeAndProviderId(ProviderType providerType, String providerId);
}

