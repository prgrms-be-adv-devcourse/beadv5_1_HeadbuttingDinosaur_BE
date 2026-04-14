package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.UserStatus;
import com.devticket.member.presentation.domain.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(UUID userId);

    @Query(
        value = """
        SELECT u FROM User u
        LEFT JOIN UserProfile p ON p.userId = u.id
        WHERE (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """,
        countQuery = """
        SELECT COUNT(u) FROM User u
        LEFT JOIN UserProfile p ON p.userId = u.id
        WHERE (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """
    )
    Page<User> searchMembers(
        @Param("role") UserRole role,
        @Param("status") UserStatus status,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    boolean existsByEmail(String email);

    Optional<User> findByProviderTypeAndProviderId(ProviderType providerType, String providerId);
    
    List<User> findByRole(UserRole role);
}

