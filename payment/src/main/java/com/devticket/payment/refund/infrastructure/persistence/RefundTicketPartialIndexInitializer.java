package com.devticket.payment.refund.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundTicketPartialIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createPartialIndex() {
        // 기존 전체 unique constraint 제거 (ddl-auto: update 환경에서 이전 버전 제약이 남아있을 수 있음)
        try {
            jdbcTemplate.execute(
                "ALTER TABLE payment.refund_ticket DROP CONSTRAINT uk_refund_ticket_ticket_id");
            log.info("[RefundTicket] 기존 uk_refund_ticket_ticket_id constraint 제거 완료");
        } catch (Exception e) {
            log.debug("[RefundTicket] uk_refund_ticket_ticket_id constraint 없음 (정상) — {}", e.getMessage());
        }

        // ACTIVE·COMPLETED 행에만 유일성을 강제하는 partial unique index 생성
        // FAILED 행은 제외되므로 실패 후 같은 ticketId로 재시도 가능
        // H2는 partial unique index 미지원 — 테스트 환경에서는 경고 후 스킵
        try {
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_ticket_active
                ON payment.refund_ticket(ticket_id)
                WHERE status = 'ACTIVE' OR status = 'COMPLETED'
                """);
            log.info("[RefundTicket] uk_refund_ticket_active partial unique index 확인/생성 완료");
        } catch (Exception e) {
            log.warn("[RefundTicket] partial unique index 생성 실패 (H2 미지원) — 운영 환경에서는 PostgreSQL 사용 필요: {}", e.getMessage());
        }
    }
}
