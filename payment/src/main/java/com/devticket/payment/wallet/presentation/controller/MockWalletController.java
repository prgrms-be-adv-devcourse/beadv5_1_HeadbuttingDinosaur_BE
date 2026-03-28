package com.devticket.payment.wallet.presentation.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/wallets")
public class MockWalletController {

    public static class MockWalletBalanceResponse {
        public UUID userId;
        public Integer balance;

        public MockWalletBalanceResponse(UUID userId, Integer balance) {
            this.userId = userId;
            this.balance = balance;
        }
    }

    @GetMapping("/{userId}/balance")
    public MockWalletBalanceResponse getWalletBalance(@PathVariable Long userId) {
        return new MockWalletBalanceResponse(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            70000
        );
    }
}
