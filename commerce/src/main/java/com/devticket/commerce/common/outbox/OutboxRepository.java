package com.devticket.commerce.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    // 스케줄러 fallback 전용 조회.
    // status = PENDING AND (nextRetryAt IS NULL OR nextRetryAt < now) AND createdAt < graceCutoff
    // graceCutoff: 직접 발행(afterCommit) 경로가 우선 처리할 수 있도록 최근 N초 이내 row 는 제외한다.
    // 재시도 케이스(nextRetryAt 설정된 row) 는 createdAt 이 충분히 과거 → grace 조건 통과.
    // 메서드명 기반 쿼리는 OR 절에 status 조건이 누락되어 SENT 레코드까지 포함되는 버그 발생 — @Query로 명시.
    @Query("""
            SELECT o FROM Outbox o
            WHERE o.status = :status
              AND (o.nextRetryAt IS NULL OR o.nextRetryAt < :now)
              AND o.createdAt < :graceCutoff
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<Outbox> findPendingToPublish(
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            @Param("graceCutoff") Instant graceCutoff);
}
