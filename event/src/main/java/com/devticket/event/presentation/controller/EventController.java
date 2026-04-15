package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.presentation.dto.SellerEventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventSummaryResponse;
import com.devticket.event.presentation.dto.SellerEventUpdateRequest;
import com.devticket.event.presentation.dto.SellerEventUpdateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<EventDetailResponse>> getEvent(
        @PathVariable("eventId") UUID eventId) {
        EventDetailResponse response = eventService.getEvent(eventId);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<EventListResponse>> getEventList(
        @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId,
        @ModelAttribute EventListRequest request,
        @PageableDefault(size = 20) Pageable pageable) {
        EventListResponse response = eventService.getEventList(request, currentUserId, pageable);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }


}