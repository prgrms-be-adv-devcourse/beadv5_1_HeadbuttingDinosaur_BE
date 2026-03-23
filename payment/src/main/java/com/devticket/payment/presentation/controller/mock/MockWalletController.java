package com.devticket.payment.presentation.controller.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mock")
@RestController
@RequestMapping("/internal/wallets")
public class MockWalletController {

    public static class MockWalletBalanceResponse {
        public Long userId;
        public Integer balance;

        public MockWalletBalanceResponse(Long userId, Integer balance) {
            this.userId = userId;
            this.balance = balance;
        }
    }

    @GetMapping("/{userId}/balance")
    public MockWalletBalanceResponse getWalletBalance(@PathVariable Long userId) {
        return new MockWalletBalanceResponse(
            42L,
            70000
        );
    }
}
