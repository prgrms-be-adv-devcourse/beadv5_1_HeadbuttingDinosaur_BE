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

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    Optional<Order> findById(Long id);

    List<Order> findAllByIdIn(List<Long> ids);

    Optional<Order> findByOrderId(UUID orderId);

    List<Order> findAllByOrderIdIn(List<UUID> orderIds);

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAllByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            value = "SELECT * FROM commerce.\"order\" WHERE status = :status AND created_at < NOW() - CAST(:minutes || ' minutes' AS INTERVAL)",
            nativeQuery = true)
    List<Order> findExpiredOrders(
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("minutes") int minutes);

}
