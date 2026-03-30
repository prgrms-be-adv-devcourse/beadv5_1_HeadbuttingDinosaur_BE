package com.devticket.payment.mock;

import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/commerce")
@Profile("local")
public class MockCommerceController {

    @GetMapping("/internal/orders/{orderId}")
    public InternalOrderInfoResponse getOrderInfo(@PathVariable String orderId) {
        return new InternalOrderInfoResponse(
            1L,
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            "test",
            50000,
            "PAYMENT_PENDING",
            "2024-01-01T00:00:00"
        );
    }

    @PostMapping("/internal/orders/{orderId}/payment-completed")
    public void completePayment(@PathVariable String orderId) {
        // 성공 응답만 반환
    }
}
