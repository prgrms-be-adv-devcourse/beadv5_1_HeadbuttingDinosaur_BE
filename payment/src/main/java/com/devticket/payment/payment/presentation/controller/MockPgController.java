package com.devticket.payment.payment.presentation.controller;

import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelResponse;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/pg")
public class MockPgController {

    public static class MockPgConfirmResponse{
        String paymentKey;
        String status;

        public MockPgConfirmResponse(String paymentKey, String status) {
            this.paymentKey = paymentKey;
            this.status = status;
        }
    }

    public static class MockPgRefundResponse{
        String status;

        public MockPgRefundResponse(String status) {
            this.status = status;
        }
    }

    @PostMapping("/v1/payments/confirm")
    public TossPaymentConfirmResponse confirmPayment(@RequestBody TossPaymentConfirmRequest request) {
        return new TossPaymentConfirmResponse(
            "mock_pg_key",
            request.orderId(),   // 요청에서 그대로 반환
            "PG",
            "DONE",
            request.amount(),    // 요청에서 그대로 반환
            "2024-01-01T00:00:00+09:00"
        );
    }

    @PostMapping("/v1/payments/{paymentKey}/cancel")
    public TossPaymentCancelResponse cancelPayment(
        @PathVariable String paymentKey,
        @RequestBody TossPaymentCancelRequest request) {
        return new TossPaymentCancelResponse(
            paymentKey,
            "mock_order_id",
            "CANCELED",
            0L,
            "2024-01-01T00:00:00+09:00",
            "2024-01-01T00:00:00+09:00"
        );
    }

    @PostMapping("/refunds")
    public MockPgRefundResponse refundPayment() {
        return new MockPgRefundResponse(
            "REFUND_SUCCESS"
        );
    }
}
