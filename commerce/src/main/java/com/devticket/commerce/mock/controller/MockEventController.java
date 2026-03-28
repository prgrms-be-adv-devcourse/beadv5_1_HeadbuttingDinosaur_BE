package com.devticket.commerce.mock.controller;

import com.devticket.commerce.mock.controller.dto.InternalStockAdjustmentRequest;
import com.devticket.commerce.mock.controller.dto.InternalStockAdjustmentResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
public class MockEventController {

    @PatchMapping("/{eventId}/stock")
    public InternalStockAdjustmentResponse mockAdjustStcok(
        @PathVariable Long eventId,
        @RequestBody InternalStockAdjustmentRequest request
    ) {
        return new InternalStockAdjustmentResponse(
            eventId,
            true,
            100,
            "이벤트 제목"
        );
    }
}