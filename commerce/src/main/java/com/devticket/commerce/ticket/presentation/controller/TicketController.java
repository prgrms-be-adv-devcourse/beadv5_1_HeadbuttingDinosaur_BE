package com.devticket.commerce.ticket.presentation.controller;

import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
@Tag(name = "Ticket API")
public class TicketController {

    private final TicketUsecase ticketUsecase;

    @PostMapping("/items")
    @Operation(description = "장바구니 담기")
    public ResponseEntity<TicketResponse> addToCart(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestBody TicketRequest request
    ) {
        TicketResponse response = ticketUsecase.createTicket(userId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
}
