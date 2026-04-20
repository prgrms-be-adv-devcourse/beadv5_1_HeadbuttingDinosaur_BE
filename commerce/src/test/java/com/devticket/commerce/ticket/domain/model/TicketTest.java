package com.devticket.commerce.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

    private Ticket ticketIn(TicketStatus status) {
        Ticket ticket = Ticket.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        setStatus(ticket, status);
        return ticket;
    }

    private static void setStatus(Ticket ticket, TicketStatus status) {
        try {
            Field field = Ticket.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(ticket, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void canTransitionTo_ISSUED_에서_CANCELLED와_REFUNDED_허용() {
        Ticket ticket = ticketIn(TicketStatus.ISSUED);
        assertThat(ticket.canTransitionTo(TicketStatus.CANCELLED)).isTrue();
        assertThat(ticket.canTransitionTo(TicketStatus.REFUNDED)).isTrue();
    }

    @Test
    void canTransitionTo_CANCELLED_에서_REFUNDED와_ISSUED_허용() {
        Ticket ticket = ticketIn(TicketStatus.CANCELLED);
        assertThat(ticket.canTransitionTo(TicketStatus.REFUNDED)).isTrue();
        assertThat(ticket.canTransitionTo(TicketStatus.ISSUED)).isTrue();
    }

    @Test
    void canTransitionTo_REFUNDED_는_종단() {
        Ticket ticket = ticketIn(TicketStatus.REFUNDED);
        for (TicketStatus target : TicketStatus.values()) {
            assertThat(ticket.canTransitionTo(target))
                .as("REFUNDED → %s 전이", target)
                .isFalse();
        }
    }

    @Test
    void cancelledTicket_ISSUED에서만_허용_CANCELLED로_전이() {
        Ticket ticket = ticketIn(TicketStatus.ISSUED);
        ticket.cancelledTicket();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void cancelledTicket_REFUNDED_상태에서는_예외() {
        Ticket ticket = ticketIn(TicketStatus.REFUNDED);
        assertThatThrownBy(ticket::cancelledTicket)
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
    }

    @Test
    void refundTicket_ISSUED_또는_CANCELLED에서_REFUNDED_전이() {
        Ticket fromIssued = ticketIn(TicketStatus.ISSUED);
        fromIssued.refundTicket();
        assertThat(fromIssued.getStatus()).isEqualTo(TicketStatus.REFUNDED);

        Ticket fromCancelled = ticketIn(TicketStatus.CANCELLED);
        fromCancelled.refundTicket();
        assertThat(fromCancelled.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void refundTicket_REFUNDED_상태에서는_예외() {
        Ticket ticket = ticketIn(TicketStatus.REFUNDED);
        assertThatThrownBy(ticket::refundTicket)
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
    }

    @Test
    void restoreToIssued_CANCELLED에서_ISSUED_로_롤백() {
        Ticket ticket = ticketIn(TicketStatus.CANCELLED);
        ticket.restoreToIssued();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
    }

    @Test
    void restoreToIssued_REFUNDED_상태에서는_예외() {
        Ticket ticket = ticketIn(TicketStatus.REFUNDED);
        assertThatThrownBy(ticket::restoreToIssued)
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", TicketErrorCode.INVALID_TICKET_STATUS_TRANSITION);
    }
}
