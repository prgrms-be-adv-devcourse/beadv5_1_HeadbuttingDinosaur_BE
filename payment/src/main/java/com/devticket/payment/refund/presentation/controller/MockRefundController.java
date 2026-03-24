package com.devticket.payment.refund.presentation.controller;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/refunds")
public class MockRefundController {

    public static class MockRefundDetailResponse {
        public Long refundId;
        public Long orderId;
        public Long userId;
        public Long paymentId;
        public Integer refundAmount;
        public Integer refundRate;
        public String status;
        public LocalDateTime requestedAt;
        public LocalDateTime completedAt;

        public MockRefundDetailResponse(Long refundId, Long orderId, Long userId,
            Long paymentId, Integer refundAmount,
            Integer refundRate, String status,
            LocalDateTime requestedAt, LocalDateTime completedAt) {
            this.refundId = refundId;
            this.orderId = orderId;
            this.userId = userId;
            this.paymentId = paymentId;
            this.refundAmount = refundAmount;
            this.refundRate = refundRate;
            this.status = status;
            this.requestedAt = requestedAt;
            this.completedAt = completedAt;
        }
    }

    public static class MockRefundsByEventResponse {
        public Long eventId;
        public List<MockRefundDetailResponse> refunds;

        public MockRefundsByEventResponse(Long eventId, List<MockRefundDetailResponse> refunds) {
            this.eventId = eventId;
            this.refunds = refunds;
        }
    }

    @GetMapping("/by-event/{eventId}")
    public MockRefundsByEventResponse getRefundsByEvent(@PathVariable Long eventId) {
        MockRefundDetailResponse refund = new MockRefundDetailResponse(
            789L,
            130L,
            55L,
            460L,
            30000,
            100,
            "COMPLETED",
            LocalDateTime.parse("2025-08-03T10:00:00"),
            LocalDateTime.parse("2025-08-03T10:01:00")
        );

        return new MockRefundsByEventResponse(
            15L,
            List.of(refund)
        );
    }
}
