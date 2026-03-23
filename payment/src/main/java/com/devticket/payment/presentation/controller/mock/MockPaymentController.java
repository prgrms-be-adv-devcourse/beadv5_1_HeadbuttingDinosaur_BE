package com.devticket.payment.presentation.controller.internal.mock;

import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mock")
@RestController
@RequestMapping("/internal/payments")
public class MockPaymentController {

    @GetMapping("/by-order/{orderId}")
    public MockPaymentResponse getPaymentByOrder(@PathVariable Long orderId) {
        return new MockInternalPaymentResponse(
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
