package com.devticket.admin.domain.repository;

import com.devticket.admin.domain.model.TechStack;
import java.util.List;
import java.util.Optional;

public interface TechStackRepository {

    List<TechStack> getTechStacks();

    TechStack save(TechStack techStack);

    Optional<TechStack> findById(Long id);

    List<TechStack> findByIdIn(List<Long> ids);

    boolean existsByName(String name);

    void deleteById(Long id);

}
