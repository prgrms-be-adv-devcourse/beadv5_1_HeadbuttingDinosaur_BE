package com.devticket.member.presentation.domain.model;

import com.devticket.member.common.exception.BusinessException;
import com.devticket.member.presentation.domain.MemberErrorCode;
import com.devticket.member.presentation.domain.ProviderType;
import com.devticket.member.presentation.domain.UserRole;
import com.devticket.member.presentation.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "\"user\"", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    // 일반 가입
    public User(String email, String password) {
        this.userId = UUID.randomUUID();
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.providerType = ProviderType.LOCAL;
        this.createdAt = LocalDateTime.now();
    }

    // 소셜 가입
    public User(String email, ProviderType providerType, String providerId) {
        this.userId = UUID.randomUUID();
        this.email = email;
        this.password = null;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.providerType = providerType;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
    }

    public void suspend() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_WITHDRAWN);
        }
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
