package com.devticket.event.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * 발행 대기 중인 Outbox 조회 — 직접 발행 fallback 경로.
     *
     * <p>조건:
     * <ul>
     *   <li>{@code status = PENDING}</li>
     *   <li>{@code nextRetryAt} 이 null 이거나 현재 시각보다 이전</li>
     *   <li>{@code createdAt < graceCutoff} — afterCommit 직접 발행 경로의 동작 시간 확보</li>
     * </ul>
     *
     * <p>경계 배제({@code <}) — `== now` 는 다음 틱에서 픽업하여 타임아웃 정합 여유 확보.
     *
     * <p>재시도 케이스({@code nextRetryAt} 이 채워진 row)는 이미 충분히 과거에 생성된 row 이므로
     * graceCutoff 조건을 자연스럽게 통과한다.
     */
    @Query("""
            SELECT o FROM Outbox o
            WHERE o.status = :status
              AND (o.nextRetryAt IS NULL OR o.nextRetryAt < :now)
              AND o.createdAt < :graceCutoff
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<Outbox> findPendingToPublish(@Param("status") OutboxStatus status,
                                      @Param("now") Instant now,
                                      @Param("graceCutoff") Instant graceCutoff);
}
