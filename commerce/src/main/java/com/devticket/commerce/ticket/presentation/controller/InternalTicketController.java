package com.devticket.commerce.ticket.presentation.controller;

import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.presentation.dto.res.InternalTicketSettlementDataResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ticket Internal API")
@RequestMapping("/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketUsecase ticketUsecase;

    // Settlement -> Commerce : eventIds별 티켓 정산 데이터 조회
    @PostMapping("/settlement-data")
    public ResponseEntity<InternalTicketSettlementDataResponse> getSettlementData(
        @RequestBody List<UUID> eventIds) {
        return ResponseEntity.ok(ticketUsecase.getSettlementData(eventIds));
    }
}