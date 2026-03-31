package com.devticket.payment.wallet.presentation.dto;

import com.devticket.payment.wallet.domain.model.WalletTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record WalletTransactionListResponse(
    List<Item> items,
    int currentPage,
    int totalPages,
    long totalElements
) {

    public static WalletTransactionListResponse of(Page<WalletTransaction> page, int currentPage) {
        List<Item> items = page.getContent().stream()
            .map(Item::of)
            .toList();
        return new WalletTransactionListResponse(
            items,
            currentPage,
            page.getTotalPages(),
            page.getTotalElements()
        );
    }

    public record Item(
        String transactionId,
        String type,           // CHARGE | USE | REFUND | WITHDRAW
        Integer amount,
        Integer balanceAfter,
        String relatedOrderId, // nullable
        String relatedRefundId, // nullable
        LocalDateTime createdAt
    ) {

        public static Item of(WalletTransaction tx) {
            return new Item(
                tx.getWalletTransactionId().toString(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getRelatedOrderId() != null ? tx.getRelatedOrderId().toString() : null,
                tx.getRelatedRefundId() != null ? tx.getRelatedRefundId().toString() : null,
                tx.getCreatedAt()
            );
        }
    }
}
