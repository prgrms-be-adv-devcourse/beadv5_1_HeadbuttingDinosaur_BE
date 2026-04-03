package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.AdminActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionHistoryJpaRepository extends JpaRepository<AdminActionHistory, Long> {


}
