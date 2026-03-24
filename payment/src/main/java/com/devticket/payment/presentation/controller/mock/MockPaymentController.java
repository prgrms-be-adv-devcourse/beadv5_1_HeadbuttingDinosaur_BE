package com.devticket.payment.presentation.controller.mock;

import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
public class MockPaymentController {

    public static class MockPaymentResponse {
        public Long id;
        public Long orderId;
        public String paymentKey;
        public String paymentMethod;
        public Integer amount;
        public String status;
        public LocalDateTime approvedAt;
        public String failureReason;

        public MockPaymentResponse(Long id, Long orderId, String paymentKey,
            String paymentMethod, Integer amount,
            String status, LocalDateTime approvedAt, String failureReason) {
            this.id = id;
            this.orderId = orderId;
            this.paymentKey = paymentKey;
            this.paymentMethod = paymentMethod;
            this.amount = amount;
            this.status = status;
            this.approvedAt = approvedAt;
            this.failureReason = failureReason;
        }
    }

    @GetMapping("/by-order/{orderId}")
    public MockPaymentResponse getPaymentByOrder(@PathVariable Long orderId) {
        return new MockPaymentResponse(
            456L,
            123L,
            "pg_abc123",
            "PG",
            130000,
            "SUCCESS",
            LocalDateTime.parse("2025-08-15T14:32:00"),
            null
        );
    }
}
