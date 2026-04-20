package com.devticket.commerce.ticket.infrastructure.persistence;

import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.model.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findAllByUserId(UUID userId, Pageable pageable);

    Optional<Ticket> findByTicketId(UUID ticketId);

    @Query
    Page<Ticket> findAllByEventId(UUID eventId, Pageable pageable);

    List<Ticket> findAllByEventIdIn(List<UUID> eventIds);

    List<Ticket> findAllByTicketIdIn(List<UUID> ticketIds);

    // OrderItem join — 주문 전체 티켓 조회 (환불 Saga, 내부 API 용)
    @Query("""
        SELECT t FROM Ticket t, OrderItem oi
        WHERE t.orderItemId = oi.orderItemId
          AND oi.orderId = :orderId
        """)
    List<Ticket> findAllByOrderId(@Param("orderId") Long orderId);

    @Query("""
        SELECT t FROM Ticket t, OrderItem oi
        WHERE t.orderItemId = oi.orderItemId
          AND oi.orderId = :orderId
          AND t.status = :status
        """)
    List<Ticket> findAllByOrderIdAndStatus(
            @Param("orderId") Long orderId,
            @Param("status") TicketStatus status);
}
