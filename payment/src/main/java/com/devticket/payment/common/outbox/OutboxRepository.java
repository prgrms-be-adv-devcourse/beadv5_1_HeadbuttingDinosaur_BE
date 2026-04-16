package com.devticket.payment.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Query("SELECT o FROM Outbox o WHERE o.status = :status " +
           "AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) " +
           "ORDER BY o.createdAt ASC LIMIT 50")
    List<Outbox> findPendingForRetry(@Param("status") OutboxStatus status,
                                     @Param("now") Instant now);
}
