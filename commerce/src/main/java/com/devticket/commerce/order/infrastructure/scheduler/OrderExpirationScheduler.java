package com.devticket.commerce.order.infrastructure.scheduler;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.application.service.OrderExpirationCancelService;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 만료 스케줄러 — PAYMENT_PENDING 상태가 30분 초과 시 CANCELLED 전이 + 재고 복구 Outbox 발행.
 *
 * <p>SchedulerLock 타이밍 설계:
 * <ul>
 *   <li>fixedDelay=60s, lockAtMostFor=2m, lockAtLeastFor=10s
 *   <li>lockAtMostFor=2m은 fixedDelay의 2배 여유 — 만료 건수가 많아 처리가 길어져도 락 자동 해제로
 *       인한 타 인스턴스 중복 진입 경로 축소
 *   <li>lockAtMostFor 〈 fixedDelay 이면 A 인스턴스 처리 중 락 만료 후 B 인스턴스가 다음 스케줄에
 *       재획득하여 중복 실행 가능 (PR #426 멘토 피드백). 2분 여유로 이 경로 차단
 * </ul>
 *
 * <p>이중 실행 시 안전성 — 타이밍상 중복 실행이 발생해도 데이터 정합성 보장:
 * <ul>
 *   <li>Order.@Version 낙관적 락 — 한쪽만 커밋 성공, 다른 쪽은 ObjectOptimisticLockingFailureException
 *   <li>canTransitionTo(CANCELLED) 선가드 — 이미 CANCELLED면 Outbox 발행 없이 스킵
 *   <li>결과: payment.failed Outbox는 최대 1회만 발행 (재고 복구 이중 실행 없음)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final int EXPIRATION_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderExpirationCancelService cancelService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "order-expiration-scheduler", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    public void cancelExpiredOrders() {
        List<Order> expiredOrders = orderRepository.findExpiredOrders(OrderStatus.PAYMENT_PENDING, EXPIRATION_MINUTES);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[OrderExpiration] 만료 대상 주문: {}건", expiredOrders.size());

        for (Order order : expiredOrders) {
            // 별도 빈 호출 → Spring AOP 프록시 경유 → per-Order @Transactional 적용
            cancelService.cancelOrder(order);
        }
    }
}
