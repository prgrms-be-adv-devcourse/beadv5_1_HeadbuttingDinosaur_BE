package com.devticket.payment.wallet.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WalletTransactionListResponse(
    List<WalletTransactionItemResponse> transactions,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static WalletTransactionListResponse from(
        List<WalletTransactionItemResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
    ) {
        return new WalletTransactionListResponse(
            transactions,
            page,
            size,
            totalElements,
            totalPages,
            hasNext
        );
    }

    public record WalletTransactionItemResponse(
        String transactionId,
        String type,
        Integer amount,
        Integer balanceAfter,
        String relatedOrderId,
        String relatedRefundId,
        LocalDateTime createdAt
    ) {
        public static WalletTransactionItemResponse of(
            String transactionId,
            String type,
            Integer amount,
            Integer balanceAfter,
            String relatedOrderId,
            String relatedRefundId,
            LocalDateTime createdAt
        ) {
            return new WalletTransactionItemResponse(
                transactionId,
                type,
                amount,
                balanceAfter,
                relatedOrderId,
                relatedRefundId,
                createdAt
            );
        }
    }
}
