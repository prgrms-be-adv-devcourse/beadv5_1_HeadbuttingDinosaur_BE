package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.model.TechStack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackRepository extends JpaRepository<TechStack, Long> {

    List<TechStack> findByIdIn(List<Long> ids);
}

