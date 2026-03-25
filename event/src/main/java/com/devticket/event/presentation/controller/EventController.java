package com.devticket.event.presentation.controller;

import com.devticket.event.application.EventService;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import com.devticket.event.common.response.SuccessResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // 인증 필수 API이므로 required = true (기본값)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<SellerEventCreateResponse> createEvent(
        @RequestHeader("X-User-Id") Long sellerId, // 내부 식별자는 Long 유지
        @Valid @RequestBody SellerEventCreateRequest request) {

        // Service가 반환하는 외부용 식별자 UUID를 받음!
        UUID eventId = eventService.createEvent(sellerId, request);

        // UUID를 Response DTO에 담아서 반환
        return SuccessResponse.created(SellerEventCreateResponse.from(eventId));
    }
}