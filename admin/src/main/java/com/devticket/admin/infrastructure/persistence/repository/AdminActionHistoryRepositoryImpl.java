package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.repository.AdminActionRepository;
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
}
