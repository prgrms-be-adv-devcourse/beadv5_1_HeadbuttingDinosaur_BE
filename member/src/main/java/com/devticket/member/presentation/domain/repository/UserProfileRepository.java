package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.model.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    boolean existsByNickname(String nickname);
}