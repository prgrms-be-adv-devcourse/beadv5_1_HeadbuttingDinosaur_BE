package com.devticket.commerce.ticket.presentation.controller;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketDetailResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket API")
public class TicketController {

    private final TicketUsecase ticketUsecase;

    @GetMapping
    @Operation(description = "나의 티켓 목록 조회")
    public ResponseEntity<TicketListResponse> getTicketList(
        @RequestHeader("X-User-Id") UUID userId,
        TicketListRequest request
    ) {

        TicketListResponse response = ticketUsecase.getTicketList(userId, request);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    @GetMapping("/{ticketId}")
    @Operation(description = "티켓 상세 정보 조회")
    public ResponseEntity<TicketDetailResponse> getTicketDetail(@PathVariable UUID ticketId) {
        TicketDetailResponse response = ticketUsecase.getTicketDetail(ticketId)
            .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    // ---- internal request---------
    @PostMapping
    @Operation(description = "티켓 발급")
    public ResponseEntity<TicketResponse> createTickets(
        @RequestBody TicketRequest request
    ) {
        TicketResponse response = ticketUsecase.createTicket(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }


}
