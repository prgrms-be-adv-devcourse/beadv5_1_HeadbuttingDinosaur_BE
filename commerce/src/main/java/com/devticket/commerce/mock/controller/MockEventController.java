package com.devticket.commerce.mock.controller;

import com.devticket.commerce.cart.infrastructure.external.client.dto.InternalPurchaseValidationResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}