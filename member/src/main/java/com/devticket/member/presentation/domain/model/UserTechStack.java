package com.devticket.member.presentation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_tech_stack", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tech_stack_id", nullable = false)
    private Long techStackId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserTechStack(Long userId, Long techStackId) {
        this.userId = userId;
        this.techStackId = techStackId;
        this.createdAt = LocalDateTime.now();
    }
}

