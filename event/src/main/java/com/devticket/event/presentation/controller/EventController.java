package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.application.event.ActionLogDomainEvent;
import com.devticket.event.common.messaging.event.ActionType;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<EventDetailResponse>> getEvent(
        @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId,
        @PathVariable("eventId") UUID eventId) {
        EventDetailResponse response = eventService.getEvent(eventId);
        if (currentUserId != null) {
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                currentUserId, eventId, ActionType.DETAIL_VIEW,
                null, null, null, null, null, Instant.now()));
        }
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<EventListResponse>> getEventList(
        @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId,
        @ModelAttribute EventListRequest request,
        @PageableDefault(size = 20) Pageable pageable) {
        EventListResponse response = eventService.getEventList(request, currentUserId, pageable);
        if (currentUserId != null) {
            String stackFilter = (request.techStacks() == null || request.techStacks().isEmpty())
                ? null
                : request.techStacks().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            eventPublisher.publishEvent(new ActionLogDomainEvent(
                currentUserId, null, ActionType.VIEW,
                request.keyword(), stackFilter,
                null, null, null, Instant.now()));
        }
        return ResponseEntity.ok(SuccessResponse.success(response));
    }


}
