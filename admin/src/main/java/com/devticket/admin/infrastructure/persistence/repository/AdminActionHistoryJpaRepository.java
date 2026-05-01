package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminTargetType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionHistoryJpaRepository extends JpaRepository<AdminActionHistory, Long> {
    List<AdminActionHistory> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
        AdminTargetType targetType, UUID targetId);
}
