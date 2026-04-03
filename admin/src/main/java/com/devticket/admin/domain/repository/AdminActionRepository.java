package com.devticket.admin.domain.repository;

import com.devticket.admin.domain.model.AdminActionHistory;

public interface AdminActionRepository {

    void save(AdminActionHistory adminActionHistory);
}
