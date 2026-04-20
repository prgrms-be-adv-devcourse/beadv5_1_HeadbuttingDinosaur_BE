package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/events")
@RequiredArgsConstructor
public class SellerEventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<SellerEventCreateResponse> createEvent(
        @RequestHeader("X-User-Id") UUID sellerId,
        @Valid @RequestBody SellerEventCreateRequest request) {
        return SuccessResponse.success(eventService.createEvent(sellerId, request));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<EventListResponse>> getSellerEvents(
        @RequestHeader("X-User-Id") UUID sellerId,
        @ModelAttribute EventListRequest request,
        @PageableDefault(size = 20) Pageable pageable) {
        // sellerId 강제 주입 — 프론트에서 안 보내도 본인 것만 조회
        EventListRequest sellerRequest = new EventListRequest(
            request.keyword(), request.category(), request.techStacks(),
            sellerId, request.status()
        );
        EventListResponse response = eventService.getEventList(sellerRequest, sellerId, pageable);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<SellerEventDetailResponse>> getSellerEventDetail(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable UUID eventId) {
        return ResponseEntity.ok(SuccessResponse.success(eventService.getSellerEventDetail(sellerId, eventId)));
    }

    @GetMapping("/{eventId}/statistics")
    public ResponseEntity<SuccessResponse<SellerEventSummaryResponse>> getEventSummary(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable UUID eventId) {
        return ResponseEntity.ok(SuccessResponse.success(eventService.getEventSummary(sellerId, eventId)));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<SuccessResponse<SellerEventUpdateResponse>> updateEvent(
        @RequestHeader("X-User-Id") UUID sellerId,
        @PathVariable UUID eventId,
        @Valid @RequestBody SellerEventUpdateRequest request) {
        return ResponseEntity.ok(SuccessResponse.success(eventService.updateEvent(sellerId, eventId, request)));
    }
}