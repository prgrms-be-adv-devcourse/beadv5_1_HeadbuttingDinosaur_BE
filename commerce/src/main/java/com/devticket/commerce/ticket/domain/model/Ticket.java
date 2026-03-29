package com.devticket.commerce.ticket.domain.model;

import com.devticket.commerce.common.entity.BaseEntity;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Getter
@Table(name = "ticket", schema = "commerce")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    UUID ticketId;

    @Column(name = "order_item_id", nullable = false)
    Long orderItemId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "event_id", nullable = false)
    Long eventId;

    @Column(name = "ticket_number", unique = true, length = 100, nullable = false)
    String ticketNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    LocalDateTime issuedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    //---- 정적 팩토리 메서드 ------------------------------

    public static Ticket create(
        Long orderItemId,
        UUID userId,
        Long eventId
    ) {
        LocalDateTime now = LocalDateTime.now();
        //티켓번호 생성 20250327-*******
        String datePrefix = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String generatedTicketNumber = String.format("%s-%s", datePrefix, uniqueSuffix);

        return Ticket.builder()
            .ticketId(UUID.randomUUID())
            .orderItemId(orderItemId)
            .userId(userId)
            .eventId(eventId)
            .ticketNumber(generatedTicketNumber)
            .status(TicketStatus.ISSUED)
            .issuedAt(now)
            .deletedAt(null)
            .build();
    }

    //---- 비즈니스 도메인 메서드 ----------------------------

    //status변경 : REFUNDED
    public void refundTicket() {
        this.status = TicketStatus.REFUNDED;
    }

    //status변경 : CANCELLED
    public void cancelledTicket() {
        this.status = TicketStatus.CANCELLED;
    }
}
