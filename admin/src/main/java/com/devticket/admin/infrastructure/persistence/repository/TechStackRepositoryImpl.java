package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.TechStack;
import com.devticket.admin.domain.repository.TechStackRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TechStackRepositoryImpl implements TechStackRepository {

    private final TechStackJpaRepository techStackJpaRepository;

    @Override
    public List<TechStack> getTechStacks() {
        return techStackJpaRepository.findAll();
    }

    @Override
    public TechStack save(TechStack techStack) {
        return techStackJpaRepository.save(techStack);
    }

    @Override
    public Optional<TechStack> findById(Long id) {
        return techStackJpaRepository.findById(id);
    }

    @Override
    public List<TechStack> findByIdIn(List<Long> ids) {
        return techStackJpaRepository.findByIdIn(ids);
    }

    @Override
    public boolean existsByName(String name) {
        return techStackJpaRepository.existsByName(name);
    }

    @Override
    public void deleteById(Long id) {
        techStackJpaRepository.deleteById(id);
    }
}
