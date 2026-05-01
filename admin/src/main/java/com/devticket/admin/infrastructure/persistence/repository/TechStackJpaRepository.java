package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.TechStack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackJpaRepository extends JpaRepository<TechStack, Long> {

    List<TechStack> findByIdIn(List<Long> ids);

    boolean existsByName(String name);
}
