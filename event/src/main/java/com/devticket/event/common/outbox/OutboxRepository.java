package com.devticket.event.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * 발행 대기 중인 Outbox 조회
     * next_retry_at이 null(즉시 대상) 이거나 현재 시각 이하인 것을 최대 50건 조회
     */
    @Query("""
            SELECT o FROM Outbox o
            WHERE o.status = :status
              AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<Outbox> findPendingOutboxes(@Param("status") OutboxStatus status,
                                     @Param("now") Instant now);
}
