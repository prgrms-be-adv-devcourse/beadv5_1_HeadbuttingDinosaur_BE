package com.devticket.commerce.ticket.presentation.controller;

import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
@Tag(name = "Ticket API")
public class TicketController {

    private final TicketUsecase ticketUsecase;

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
