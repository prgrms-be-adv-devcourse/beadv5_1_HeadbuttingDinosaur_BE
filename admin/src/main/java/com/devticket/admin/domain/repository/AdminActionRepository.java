package com.devticket.admin.domain.repository;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminTargetType;
import java.util.List;
import java.util.UUID;

public interface AdminActionRepository {
    void save(AdminActionHistory adminActionHistory);
    List<AdminActionHistory> findByTarget(AdminTargetType targetType, UUID targetId);
}
