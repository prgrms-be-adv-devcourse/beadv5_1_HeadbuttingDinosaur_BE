package com.devticket.commerce.common.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    // next_retry_at이 null(즉시 처리 대상)이거나 현재 시각 이전인 PENDING 레코드를 생성 순으로 조회
    // 한 번에 최대 50건 처리 — 스케줄러 폴링 주기 3초 기준
    List<Outbox> findTop50ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            OutboxStatus status1, OutboxStatus status2, Instant now);
}
