package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<EventDetailResponse>> getEvent(
        @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId,
        @PathVariable("eventId") UUID eventId) {
        EventDetailResponse response = eventService.getEvent(eventId);
        eventService.logDetailView(currentUserId, eventId);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<EventListResponse>> getEventList(
        @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId,
        @ModelAttribute EventListRequest request,
        @PageableDefault(size = 20) Pageable pageable) {
        EventListResponse response = eventService.getEventList(request, currentUserId, pageable);
        eventService.logEventListView(currentUserId, request);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }


}
