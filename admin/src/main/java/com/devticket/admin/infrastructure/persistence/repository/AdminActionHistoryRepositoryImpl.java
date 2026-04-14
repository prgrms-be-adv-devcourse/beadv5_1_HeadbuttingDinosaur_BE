package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminActionHistoryRepositoryImpl implements AdminActionRepository {

    private final AdminActionHistoryJpaRepository adminActionHistoryJpaRepository;


    @Override
    public void save(AdminActionHistory adminActionHistory) {
        adminActionHistoryJpaRepository.save(adminActionHistory);
    }

    @Override
    public List<AdminActionHistory> findByTarget(AdminTargetType targetType, UUID targetId) {
        return adminActionHistoryJpaRepository
            .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }
}
