package com.devticket.commerce.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    // status = PENDING AND (nextRetryAt IS NULL OR nextRetryAt < now) 조건으로 조회
    // 메서드명 기반 쿼리는 OR 절에 status 조건이 누락되어 SENT 레코드까지 포함되는 버그 발생 — @Query로 명시
    // 한 번에 최대 50건 처리 — 스케줄러 폴링 주기 3초 기준
    @Query("""
            SELECT o FROM Outbox o
            WHERE o.status = :status
              AND (o.nextRetryAt IS NULL OR o.nextRetryAt < :now)
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<Outbox> findPendingToPublish(@Param("status") OutboxStatus status, @Param("now") Instant now);
}
