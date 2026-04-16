package com.devticket.payment.payment.application.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletPgTimeoutScheduler {

    private static final int TIMEOUT_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final WalletPgTimeoutHandler timeoutHandler;

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "wallet-pg-timeout", lockAtMostFor = "50s", lockAtLeastFor = "10s")
    public void processExpiredWalletPgPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<Payment> expiredPayments = paymentRepository.findExpiredReadyPayments(
            PaymentStatus.READY, PaymentMethod.WALLET_PG, cutoff
        );

        if (expiredPayments.isEmpty()) {
            return;
        }

        log.info("[WalletPgTimeout] 만료 WALLET_PG 결제 {}건 처리 시작", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            try {
                timeoutHandler.processTimeout(payment);
            } catch (Exception e) {
                log.error("[WalletPgTimeout] 타임아웃 처리 실패 — orderId={}, error={}",
                    payment.getOrderId(), e.getMessage(), e);
            }
        }

        log.info("[WalletPgTimeout] 만료 WALLET_PG 결제 처리 완료");
    }
}
