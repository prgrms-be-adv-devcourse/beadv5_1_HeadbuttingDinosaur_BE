package com.devticket.commerce.ticket.application.usecase;

import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.SellerEventParticipantListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import java.util.UUID;

public interface TicketUsecase {

    TicketListResponse getTicketList(UUID userId, TicketListRequest request);

    // --- internal request
    TicketResponse createTicket(TicketRequest request);

    // 이벤트 참가자 조회
    SellerEventParticipantListResponse getParticipantList(UUID userId, Long eventId,
        SellerEventParticipantListRequest request);
}
