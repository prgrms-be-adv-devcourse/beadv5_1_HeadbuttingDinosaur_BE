package com.devticket.commerce.order.application.service;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 만료 취소 처리 — OrderExpirationScheduler의 per-Order 트랜잭션을 담당.
 *
 * <p>스케줄러와 별도 빈으로 분리한 이유:
 * {@code OrderExpirationScheduler} 내부에서 같은 클래스 메서드를 직접 호출하면
 * Spring AOP 프록시가 우회되어 {@code @Transactional}이 적용되지 않음.
 * 별도 빈 주입 방식으로 호출해야 프록시를 통해 트랜잭션이 열림.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationCancelService {

    private final OrderRepository orderRepository;

    @Transactional
    public void cancelOrder(Order order) {
        try {
            if (!order.canTransitionTo(OrderStatus.CANCELLED)) {
                log.info("[OrderExpiration] 스킵 — orderId={}, 현재상태={}",
                        order.getOrderId(), order.getStatus());
                return;
            }

            order.cancel();
            orderRepository.save(order);

            log.info("[OrderExpiration] 만료 처리 완료 — orderId={}", order.getOrderId());
        } catch (ObjectOptimisticLockingFailureException e) {
            // Consumer와 동시 충돌 — 재조회 후 판단
            Order refreshed = orderRepository.findByOrderId(order.getOrderId()).orElse(null);
            if (refreshed == null) {
                log.warn("[OrderExpiration] 재조회 실패 — orderId={}", order.getOrderId());
                return;
            }

            if (refreshed.getStatus() == OrderStatus.PAID
                    || refreshed.getStatus() == OrderStatus.FAILED
                    || refreshed.getStatus() == OrderStatus.CANCELLED) {
                log.info("[OrderExpiration] 충돌 후 스킵 — orderId={}, 현재상태={}",
                        refreshed.getOrderId(), refreshed.getStatus());
            } else {
                log.warn("[OrderExpiration] 충돌 후 재시도 필요 — orderId={}, 현재상태={}",
                        refreshed.getOrderId(), refreshed.getStatus());
            }
        }
    }
}
