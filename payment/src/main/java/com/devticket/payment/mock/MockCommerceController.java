package com.devticket.payment.mock;

import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment 단독 수동 테스트용 Mock 엔드포인트.
 * test 프로필에서만 활성화.
 */
@RestController
@Profile("test")
@RequiredArgsConstructor
public class MockCommerceController {

    private static final UUID MOCK_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final WalletRepository walletRepository;

    @GetMapping("/internal/orders/{orderId}")
    public InternalOrderInfoResponse getOrderInfo(@PathVariable UUID orderId) {
        return new InternalOrderInfoResponse(
            orderId,
            MOCK_USER_ID,
            "MOCK-ORD-001",
            50000,
            "PAYMENT_PENDING",
            "2025-01-01T00:00:00",
            List.of(
                new InternalOrderInfoResponse.OrderItem(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440010"), 2
                )
            )
        );
    }

    @GetMapping("/internal/order-items/by-ticket/{ticketId}")
    public InternalOrderItemInfoResponse getOrderItemByTicketId(@PathVariable String ticketId) {
        return new InternalOrderItemInfoResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MOCK_USER_ID,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
            50000
        );
    }

    /**
     * 테스트용 지갑 잔액 충전. PG 연동 없이 DB에 직접 반영.
     * POST /mock/wallet/charge?userId=...&amount=100000
     */
    @PostMapping("/mock/wallet/charge")
    @Transactional
    public Map<String, Object> mockCharge(
        @RequestParam UUID userId,
        @RequestParam int amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseGet(() -> walletRepository.save(Wallet.create(userId)));
        walletRepository.chargeBalanceAtomic(userId, amount);
        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        return Map.of("userId", userId, "charged", amount, "balance", updated.getBalance());
    }
}
