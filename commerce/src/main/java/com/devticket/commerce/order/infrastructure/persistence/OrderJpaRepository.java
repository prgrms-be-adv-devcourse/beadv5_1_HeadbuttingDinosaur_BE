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

    @Query(
            value = "SELECT * FROM commerce.\"order\" WHERE status = :status AND created_at < NOW() - CAST(:minutes || ' minutes' AS INTERVAL)",
            nativeQuery = true)
    List<Order> findExpiredOrders(
            @Param("status") String status,
            @Param("minutes") int minutes);

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

}
