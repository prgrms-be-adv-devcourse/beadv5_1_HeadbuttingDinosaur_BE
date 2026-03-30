package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderItemId(UUID orderItemId);

    List<OrderItem> findAllByOrderId(Long orderId);

    @Query("SELECT oi FROM OrderItem oi " +
        "WHERE oi.userId = :sellerId " +
        "AND oi.createdAt >= :periodStart " +
        "AND oi.createdAt <= :periodEnd")
    List<OrderItem> findSettlementItems(
        @Param("sellerId") UUID sellerId,
        @Param("periodStart") LocalDateTime periodStart,
        @Param("periodEnd") LocalDateTime periodEnd
    );

    List<OrderItem> findAllByEventId(Long eventId);

    Optional<OrderItem> findByOrderItemId(UUID orderItemId);
}
