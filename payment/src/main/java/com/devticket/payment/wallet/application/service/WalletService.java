package com.devticket.payment.wallet.application.service;

import com.devticket.payment.wallet.application.event.PaymentCompletedEvent;
import com.devticket.payment.wallet.presentation.dto.SettlementDepositRequest;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import java.util.List;
import java.util.UUID;

public interface WalletService {

    WalletChargeResponse charge(UUID userId, WalletChargeRequest request, String idempotencyKey);

    WalletChargeConfirmResponse confirmCharge(UUID userId, WalletChargeConfirmRequest request);

    void failCharge(UUID userId, String chargeId);

    WalletWithdrawResponse withdraw(UUID userId, WalletWithdrawRequest request);

    WalletBalanceResponse getBalance(UUID userId);

    WalletTransactionListResponse getTransactions(UUID userId, int page, int size);

    void processWalletPayment(UUID userId, UUID orderId, int amount,
        List<PaymentCompletedEvent.OrderItem> orderItems);

    void restoreBalance(UUID userId, int amount, UUID refundId, UUID orderId);

    void deductForWalletPg(UUID userId, UUID orderId, int walletAmount);

    void restoreForWalletPgFail(UUID userId, int walletAmount, UUID orderId);

    void processBatchRefund(UUID eventId);

    void recoverStalePendingCharge(UUID chargeId);

    void depositFromSettlement(SettlementDepositRequest request);
}