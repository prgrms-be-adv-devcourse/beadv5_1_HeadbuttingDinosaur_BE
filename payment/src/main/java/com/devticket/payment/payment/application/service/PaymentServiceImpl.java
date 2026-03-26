package com.devticket.payment.payment.application.service;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CommerceInternalClient commerceInternalClient;

    //결제 준비
    @Override
    @Transactional
    public PaymentReadyResponse readyPayment(Long userId, PaymentReadyRequest request) {
        // 주문 조회 및 검증
        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());
        validateOrderOwner(userId, order.userId());
        validateOrderPayable(order);

        //Payment 생성
        Payment payment = Payment.create(
            order.id(),
            userId,
            request.paymentMethod(),
            order.totalAmount()
        );

        Payment savedPayment = paymentRepository.save(payment);

        //예치금 결제일 경우 - 바로 결제
        if (request.paymentMethod() == PaymentMethod.WALLET) {
            return processWalletPayment(userId, order, request.orderId(), savedPayment);
        }

        return PaymentReadyResponse.from(
            savedPayment,
            request.orderId(),
            order.orderNumber(),
            order.status()
        );
    }

    //예치금 결제
    private PaymentReadyResponse processWalletPayment(
        Long userId,
        InternalOrderInfoResponse order,
        String orderId,
        Payment payment
    ) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        String transactionKey = "WALLET:PAY:" + order.id();

        boolean exists = walletTransactionRepository.existsByTransactionKey(transactionKey);
        if (exists) {
            throw new PaymentException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        if (wallet.getBalance() < order.totalAmount()) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        //잔액 차감
        wallet.use(payment.getAmount());

        //wallet transaction 생성
        WalletTransaction walletTransaction = WalletTransaction.createUse(
            wallet.getId(),
            userId,
            transactionKey,
            payment.getAmount(),
            wallet.getBalance(),
            payment.getOrderId()
        );

        walletTransactionRepository.save(walletTransaction);

        //결제 상태 SUCCESS로 변경
        payment.approve("WALLET-" + payment.getPaymentId());

        return PaymentReadyResponse.from(
            payment,
            orderId,
            order.orderNumber(),
            order.status()
        );
    }

    //올바른 사용자인지 확인
    private void validateOrderOwner(Long requestUserId, Long orderUserId) {
        if (!requestUserId.equals(orderUserId)) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    //주문이 결제 가능 상태인지 확인
    private void validateOrderPayable(InternalOrderInfoResponse order) {
        if (!order.status().equals("PAYMENT_PENDING")) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }
}
