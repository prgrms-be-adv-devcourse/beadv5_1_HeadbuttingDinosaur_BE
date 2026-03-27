package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import com.devticket.event.common.response.SuccessResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<SellerEventCreateResponse> createEvent(
        @RequestHeader("X-User-Id") Long sellerId, // 내부 식별자는 Long 유지
        @Valid @RequestBody SellerEventCreateRequest request) {

        UUID eventId = eventService.createEvent(sellerId, request);

        return SuccessResponse.created(SellerEventCreateResponse.from(eventId));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<EventDetailResponse>> getEvent(
        @PathVariable("eventId") UUID eventId) {

        EventDetailResponse response = eventService.getEvent(eventId);

        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<EventListResponse>> getEventList(
        @ModelAttribute EventListRequest request,
        @PageableDefault(size = 20) Pageable pageable) {

        EventListResponse response = eventService.getEventList(request, pageable);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

}