package com.devticket.payment.wallet.presentation.controller;

import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.presentation.dto.SettlementDepositRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/wallet")
@RequiredArgsConstructor
public class WalletInternalController {

    private final WalletService walletService;

    /**
     * Settlement → Payment: 정산금을 판매자 예치금으로 전환.
     * 성공 204 → Settlement가 지급완료 처리, 실패 4xx/5xx → 지급실패 처리.
     */
    @PostMapping("/settlement-deposit")
    public ResponseEntity<Void> depositFromSettlement(
        @Valid @RequestBody SettlementDepositRequest request) {
        walletService.depositFromSettlement(request);
        return ResponseEntity.noContent().build();
    }
}