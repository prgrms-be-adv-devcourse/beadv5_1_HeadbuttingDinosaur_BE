package com.devticket.commerce.order.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OrderSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    // PAYMENT_PENDING 상태인 행에만 (user_id, cart_hash) 유니크 제약 적용
    // CANCELLED/FAILED 이력 주문은 인덱스 대상 제외 → 동일 cartHash 이력 공존 허용
    @EventListener(ApplicationReadyEvent.class)
    public void createActiveOrderDedupIndex() {
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_order_active_dedup
            ON commerce."order" (user_id, cart_hash)
            WHERE status = 'PAYMENT_PENDING'
        """);
        log.info("[OrderSchemaInitializer] idx_order_active_dedup 확인 완료");
    }
}
