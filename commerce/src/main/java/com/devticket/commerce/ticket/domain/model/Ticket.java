package com.devticket.commerce.ticket.domain.model;

import com.devticket.commerce.common.entity.BaseEntity;
import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
    UUID orderItemId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "ticket_number", unique = true, length = 100, nullable = false)
    String ticketNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    LocalDateTime issuedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    // 낙관적 락 — Refund Saga 동시 전이 / 보상 롤백 충돌 방어
    @Version
    @Column(name = "version", nullable = false)
    Long version;

    //---- 정적 팩토리 메서드 ------------------------------

    public static Ticket create(
        UUID orderItemId,
        UUID userId,
        UUID eventId
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

    // refund.completed 수신 시 최종 확정 — CANCELLED → REFUNDED (Saga 1단계에서 CANCELLED 선행 필수)
    // 단 ISSUED → REFUNDED 직접 전이도 허용 (레거시 경로 및 단건 환불 보완 시나리오)
    public void refundTicket() {
        if (!canTransitionTo(TicketStatus.REFUNDED)) {
            throw new BusinessException(TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
        }
        this.status = TicketStatus.REFUNDED;
    }

    // refund.ticket.cancel 수신: ISSUED → CANCELLED (Saga 2단계)
    public void cancelledTicket() {
        if (!canTransitionTo(TicketStatus.CANCELLED)) {
            throw new BusinessException(TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
        }
        this.status = TicketStatus.CANCELLED;
    }

    // refund.ticket.compensate 수신: CANCELLED → ISSUED (보상 롤백)
    public void restoreToIssued() {
        if (!canTransitionTo(TicketStatus.ISSUED)) {
            throw new BusinessException(TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
        }
        this.status = TicketStatus.ISSUED;
    }

    //---- 상태 전이 검증 ------------------------------

    public boolean canTransitionTo(TicketStatus target) {
        return switch (this.status) {
            // ISSUED: 발급 완료 — 환불 Saga 진입(CANCELLED) 또는 단건 직접 환불(REFUNDED)
            case ISSUED    -> target == TicketStatus.CANCELLED
                           || target == TicketStatus.REFUNDED;
            // CANCELLED: Saga 중간 상태 — 최종 확정(REFUNDED) 또는 보상 롤백(ISSUED)
            case CANCELLED -> target == TicketStatus.REFUNDED
                           || target == TicketStatus.ISSUED;
            // REFUNDED: 종단 상태 — 전이 불가
            default        -> false;
        };
    }
}
