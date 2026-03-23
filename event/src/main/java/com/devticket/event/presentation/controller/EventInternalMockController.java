package com.devticket.event.presentation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/events")
@Profile("mock")
@Tag(name = "Event Internal API", description = "Commerce, Payment, Settlement 서비스 연동용 내부 API (Mock)")
public class EventInternalMockController {

    // ==========================================
    // Request & Response DTO (Inner Record)
    // ==========================================

    // 1. Request DTOs
    public record Internal_QueryParam_PurchaseValidation(Integer requestedQuantity) {}
    public record InternalStockDeductRequest(Integer quantity) {}
    public record InternalStockRestoreRequest(Integer quantity) {}
    public record Internal_QueryParam_SellerEvents(String status) {}

    // 2. Response DTOs
    public record InternalEventInfoResponse(
        Long id, Long sellerId, String title, Integer price,
        String status, String category, Integer totalQuantity,
        Integer maxQuantity, Integer remainingQuantity,
        String eventDateTime, String saleStartAt, String saleEndAt
    ) {}
    public record InternalPurchaseValidationResponse(Long eventId, Boolean purchasable, String reason) {}
    public record InternalStockOperationResponse(Long eventId, Boolean success, Integer remainingQuantity) {}
    public record InternalSellerEventsResponse(Long sellerId, List<SellerEventDto> events) {
        public record SellerEventDto(
            Long eventId, String title, Integer price,
            Integer totalQuantity, Integer remainingQuantity,
            String status, String eventDateTime
        ) {}
    }


    // ==========================================
    // API Endpoints
    // ==========================================

    @GetMapping("/{eventId}")
    public ResponseEntity<InternalEventInfoResponse> getEventMock(
        @PathVariable Long eventId) {
        return ResponseEntity.ok(new InternalEventInfoResponse(
            eventId, 7L, "Spring Boot 심화 밋업", 30000,
            "ON_SALE", "MEETUP", 50, 4, 23,
            "2025-08-15T19:00:00", "2025-08-01T00:00:00", "2025-08-14T23:59:59"
        ));
    }

    @GetMapping("/{eventId}/validate-purchase")
    public ResponseEntity<InternalPurchaseValidationResponse> validatePurchaseMock(
        @PathVariable Long eventId,
        @ModelAttribute Internal_QueryParam_PurchaseValidation queryParam) {
        return ResponseEntity.ok(new InternalPurchaseValidationResponse(eventId, true, null));
    }

    @PostMapping("/{eventId}/deduct-stock")
    public ResponseEntity<InternalStockOperationResponse> deductStockMock(
        @PathVariable Long eventId,
        @RequestBody InternalStockDeductRequest request) {
        return ResponseEntity.ok(new InternalStockOperationResponse(eventId, true, 21));
    }

    @PostMapping("/{eventId}/restore-stock")
    public ResponseEntity<InternalStockOperationResponse> restoreStockMock(
        @PathVariable Long eventId,
        @RequestBody InternalStockRestoreRequest request) {
        return ResponseEntity.ok(new InternalStockOperationResponse(eventId, true, 24));
    }

    @GetMapping("/by-seller/{sellerId}")
    public ResponseEntity<InternalSellerEventsResponse> getEventsBySellerMock(
        @PathVariable Long sellerId,
        @ModelAttribute Internal_QueryParam_SellerEvents queryParam) {
        List<InternalSellerEventsResponse.SellerEventDto> mockEvents = List.of(
            new InternalSellerEventsResponse.SellerEventDto(
                15L, "Spring Boot 심화 밋업", 30000, 50, 23, "SALE_ENDED", "2025-08-15T19:00:00"
            )
        );
        return ResponseEntity.ok(new InternalSellerEventsResponse(sellerId, mockEvents));
    }
}