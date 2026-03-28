package com.devticket.payment.wallet.presentation.controller;

import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.presentation.dto.WalletBalanceResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import com.devticket.payment.wallet.presentation.dto.WalletTransactionListResponse;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawRequest;
import com.devticket.payment.wallet.presentation.dto.WalletWithdrawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
@Tag(name = "Wallet", description = "예치금 API")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @Operation(summary = "예치금 충전 시작", description = "PG 결제를 통한 예치금 충전을 시작합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 충전 시작 성공")
    @ApiResponse(responseCode = "400", description = "충전 금액이 올바르지 않습니다.")
    @PostMapping("/wallet/charge")
    public ResponseEntity<WalletChargeResponse> charge(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody WalletChargeRequest request) {

        WalletChargeResponse response = walletService.charge(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 충전 승인", description = "PG 승인 결과를 바탕으로 예치금 충전을 확정합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 충전 승인 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 승인 요청입니다.")
    @ApiResponse(responseCode = "502", description = "PG 승인 처리에 실패했습니다.")
    @PostMapping("/wallets/charge/confirm")
    public ResponseEntity<WalletChargeConfirmResponse> confirmCharge(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody WalletChargeConfirmRequest request) {

        WalletChargeConfirmResponse response = walletService.confirmCharge(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 잔액 조회", description = "본인 예치금 잔액을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 잔액 조회 성공")
    @ApiResponse(responseCode = "404", description = "예치금 지갑을 찾을 수 없습니다.")
    @GetMapping("/wallet")
    public ResponseEntity<WalletBalanceResponse> getBalance(
        @RequestHeader("X-User-Id") UUID userId) {

        WalletBalanceResponse response = walletService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 거래 내역 조회", description = "본인 예치금 거래 내역을 페이징 조회합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 거래 내역 조회 성공")
    @ApiResponse(responseCode = "400", description = "페이지 파라미터가 올바르지 않습니다.")
    @GetMapping("/wallet/transactions")
    public ResponseEntity<WalletTransactionListResponse> getTransactions(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        WalletTransactionListResponse response = walletService.getTransactions(userId, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 출금 요청", description = "본인 예치금 출금을 요청합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 출금 요청 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 출금 요청입니다.")
    @ApiResponse(responseCode = "409", description = "예치금 잔액이 부족합니다.")
    @PostMapping("/wallet/withdraw")
    public ResponseEntity<WalletWithdrawResponse> withdraw(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody WalletWithdrawRequest request) {

        WalletWithdrawResponse response = walletService.withdraw(userId, request);
        return ResponseEntity.ok(response);
    }
}