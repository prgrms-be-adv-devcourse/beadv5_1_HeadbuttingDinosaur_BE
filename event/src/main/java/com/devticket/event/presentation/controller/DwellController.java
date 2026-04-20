package com.devticket.event.presentation.controller;

import com.devticket.event.application.event.ActionLogDomainEvent;
import com.devticket.event.common.messaging.event.ActionType;
import com.devticket.event.presentation.dto.DwellRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class DwellController {

    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/{eventId}/dwell")
    public ResponseEntity<Void> reportDwell(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody DwellRequest request) {
        if (userId != null) {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                    userId, eventId, ActionType.DWELL_TIME,
                    null, null, request.dwellTimeSeconds(), null, null, Instant.now()));
        }
        return ResponseEntity.noContent().build();
    }
}
