package com.devticket.commerce.mock.controller;

import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalBulkStockAdjustmentRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentRequest;
import com.devticket.commerce.order.infrastructure.external.client.dto.InternalStockAdjustmentResponse;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
public class MockEventController {

    /**
     * EventClient의 요청을 로컬에서 대신 받아주는 Mock API
     */
    @GetMapping("/{eventId}/validate-purchase")
    public InternalPurchaseValidationResponse mockValidatePurchase(
        @PathVariable Long eventId,
        @RequestParam UUID userId,
        @RequestParam Integer quantity) {

        // 로컬 테스트를 위한 가짜(Mock) 응답 객체 생성
        return new InternalPurchaseValidationResponse(
            eventId,              // 1. Long eventId
            true,                 // 2. Boolean purchasable
            "AVAILABLE",          // 3. String reason
            100,                  // 4. int maxQuantity
            "Mock 이벤트 제목",     // 5. String title
            500                   // 6. int price
        );
    }

    @PatchMapping("/{eventId}/stock")
    public InternalStockAdjustmentResponse mockAdjustStcok(
        @PathVariable Long eventId,
        @RequestBody InternalStockAdjustmentRequest request
    ) {
        return new InternalStockAdjustmentResponse(
            eventId,
            true,
            100,
            "이벤트 제목",
            30000,
            10
        );
    }

    @PatchMapping("/stock-adjustments")
    public List<InternalStockAdjustmentResponse> mockBulkAdjustStock(
        @RequestBody InternalBulkStockAdjustmentRequest request
    ) {
        return request.eventItems().stream()
            .map(item -> new InternalStockAdjustmentResponse(
                item.eventId(),
                true,
                100,
                "이벤트 제목",
                30000,
                10
            ))
            .toList();
    }


    @PostMapping("/bulk")
    public List<InternalEventInfoResponse> getBulkEventInfo(
        @RequestBody InternalBulkEventInfoRequest request
    ) {
        return List.of(
            new InternalEventInfoResponse(
                15L,
                UUID.fromString("1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001"),
                "Spring 밋업 (Mock)",
                30000,
                "ON_SALE",
                "MEETUP",
                50,
                4,
                23,
                "2026-04-01T14:00:00",
                "2026-03-01T00:00:00",
                "2026-03-31T23:59:59"
            ),
            new InternalEventInfoResponse(
                102L,
                UUID.fromString("48e57d51-0d3f-4404-8c4e-f9d7ef710002"),
                "Java 컨퍼런스 (Mock)",
                30000,
                "ON_SALE",
                "CONFERENCE",
                100,
                4,
                50,
                "2026-05-15T10:00:00",
                "2026-04-01T00:00:00",
                "2026-05-14T23:59:59"
            ),
            new InternalEventInfoResponse(
                103L,
                UUID.fromString("99b32c12-3d4f-4ee1-b22a-1cd2fdb10005"),
                "MSA 아키텍처 특강 (Mock)",
                30000,
                "ON_SALE",
                "MEETUP",
                30,
                2,
                10,
                "2026-06-20T19:30:00",
                "2026-05-01T00:00:00",
                "2026-06-19T23:59:59"
            )
        );
    }

    @GetMapping("/{eventId}")
    public InternalEventInfoResponse getSingleEventInfo(
        @PathVariable Long eventId
    ) {
        return new InternalEventInfoResponse(
            eventId,
            UUID.fromString("1d7f4d4a-1c6b-4aa2-b49e-8ed2fdb10001"),
            "Spring 밋업 (Mock)",
            30000,
            "ON_SALE",
            "MEETUP",
            50,
            4,
            23,
            "2026-04-01T14:00:00",
            "2026-03-01T00:00:00",
            "2026-03-31T23:59:59"
        );
    }
}