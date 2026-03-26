package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.model.UserTechStack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTechStackRepository extends JpaRepository<UserTechStack, Long> {

    List<UserTechStack> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
