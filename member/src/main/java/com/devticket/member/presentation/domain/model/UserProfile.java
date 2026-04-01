package com.devticket.member.presentation.domain.model;

import com.devticket.member.presentation.domain.Position;
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
@Table(name = "user_profile", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_profile_id", unique = true, nullable = false, updatable = false)
    private UUID userProfileId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 12)
    private String nickname;

    @Column(name = "profile_img_url")
    private String profileImgUrl;

    @Enumerated(EnumType.STRING)
    private Position position;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserProfile(Long userId, String nickname, Position position,
        String profileImgUrl, String bio) {
        this.userProfileId = UUID.randomUUID();
        this.userId = userId;
        this.nickname = nickname;
        this.position = position;
        this.profileImgUrl = profileImgUrl;
        this.bio = bio;
        this.createdAt = LocalDateTime.now();
    }

    public void update(String nickname, Position position,
        String profileImgUrl, String bio) {
        this.nickname = nickname;
        this.position = position;
        this.profileImgUrl = profileImgUrl;
        this.bio = bio;
        this.updatedAt = LocalDateTime.now();
    }
}