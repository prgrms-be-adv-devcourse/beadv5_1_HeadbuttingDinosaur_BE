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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final int EXPIRATION_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderExpirationCancelService cancelService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "order-expiration-scheduler", lockAtMostFor = "50s", lockAtLeastFor = "10s")
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
