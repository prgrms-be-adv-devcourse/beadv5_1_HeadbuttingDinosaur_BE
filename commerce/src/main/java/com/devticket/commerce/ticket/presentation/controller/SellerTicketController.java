package com.devticket.commerce.ticket.presentation.controller;

import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.res.SellerEventParticipantListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seller/events")
@Tag(name = "Seller Ticket API", description = "판매자 대상 티켓 API")
public class SellerTicketController {

    private final TicketUsecase ticketUsecase;

    @GetMapping("/{eventId}/participants")
    @Operation(description = "이벤트 참여자 목록 조회")
    public ResponseEntity<SellerEventParticipantListResponse> getParticipantList(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable UUID eventId,
        @ModelAttribute SellerEventParticipantListRequest request
    ) {
        SellerEventParticipantListResponse response = ticketUsecase.getParticipantList(userId, eventId, request);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }
}
