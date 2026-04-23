package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    Optional<Order> findById(Long id);

    List<Order> findAllByIdIn(List<Long> ids);

    Optional<Order> findByOrderId(UUID orderId);

    List<Order> findAllByOrderIdIn(List<UUID> orderIds);

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAllByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    // 만료 조건: status + updatedAt < threshold
    // PAYMENT_PENDING 진입 시각 기준 — BaseEntity.updated_at (@LastModifiedDate) 재활용
    // 이유: created_at 기준은 CREATED 진입 시각이라 stock.deducted 지연 시 결제 시간 단축 문제 발생 (PR #426 Codex P2)
    // 가정: PAYMENT_PENDING 상태에서 Order 엔티티 mutation 경로 없음 (Order.updateTotalAmount() @Deprecated)
    // 근거: docs/kafka-impl-plan.md §주문 만료 스케줄러
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.updatedAt < :threshold")
    List<Order> findExpiredOrders(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Query("""
            SELECT o FROM Order o
            WHERE o.userId = :userId
              AND o.cartHash = :cartHash
              AND o.status IN :activeStatuses
            """)
    Optional<Order> findActiveOrder(
            @Param("userId") UUID userId,
            @Param("cartHash") String cartHash,
            @Param("activeStatuses") List<OrderStatus> activeStatuses
    );

    // Admin/Seller 강제 취소 fan-out용 — 해당 eventId의 PAID Order 를 OrderItem join 으로 조회
    @Query("""
            SELECT DISTINCT o FROM Order o, OrderItem oi
            WHERE oi.orderId = o.id
              AND oi.eventId = :eventId
              AND o.status = :status
            """)
    List<Order> findAllByEventIdAndStatus(
            @Param("eventId") UUID eventId,
            @Param("status") OrderStatus status
    );

}
