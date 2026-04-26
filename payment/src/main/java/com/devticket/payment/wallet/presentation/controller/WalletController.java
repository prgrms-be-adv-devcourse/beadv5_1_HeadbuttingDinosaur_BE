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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "예치금 API")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    //WalletCharge(충전요청서) 생성 : 예치금 충전시 PG결제창 연결을 위한 chargeId생성
    @Operation(summary = "예치금 충전 시작", description = "PG 결제를 통한 예치금 충전을 시작합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 충전 시작 성공")
    @ApiResponse(responseCode = "400", description = "충전 금액이 올바르지 않습니다.")
    @PostMapping("/charge")
    public ResponseEntity<WalletChargeResponse> charge(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody WalletChargeRequest request) {

        //예치금 충전시 PG사 결제결제인증에 필요한  orderId
        WalletChargeResponse response = walletService.charge(userId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    //WalletCharge 충전 요청 상태를 실패로 처리 (PG 결제창에서 취소/실패 시 클라이언트에서 호출)
    @Operation(summary = "예치금 충전 실패 처리", description = "PENDING 상태의 충전 요청을 FAILED로 변경합니다.")
    @ApiResponse(responseCode = "204", description = "충전 실패 처리 성공")
    @ApiResponse(responseCode = "404", description = "충전 요청을 찾을 수 없습니다.")
    @ApiResponse(responseCode = "409", description = "대기 상태가 아닌 충전 건입니다.")
    @PatchMapping("/charge/{chargeId}/fail")
    public ResponseEntity<Void> failCharge(
        @RequestHeader("X-User-Id") UUID userId,
        @PathVariable String chargeId) {

        walletService.failCharge(userId, chargeId);
        return ResponseEntity.noContent().build();
    }

    //PG결제인증 완료건에 대해서 최종 결제승인.
    @Operation(summary = "예치금 충전 승인", description = "PG 승인 결과를 바탕으로 예치금 충전을 확정합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 충전 승인 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 승인 요청입니다.")
    @ApiResponse(responseCode = "502", description = "PG 승인 처리에 실패했습니다.")
    @PostMapping("/charge/confirm")
    public ResponseEntity<WalletChargeConfirmResponse> confirmCharge(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody WalletChargeConfirmRequest request) {

        WalletChargeConfirmResponse response = walletService.confirmCharge(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 잔액 조회", description = "본인 예치금 잔액을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 잔액 조회 성공")
    @ApiResponse(responseCode = "404", description = "예치금 지갑을 찾을 수 없습니다.")
    @GetMapping()
    public ResponseEntity<WalletBalanceResponse> getBalance(
        @RequestHeader("X-User-Id") UUID userId) {

        WalletBalanceResponse response = walletService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예치금 거래 내역 조회", description = "본인 예치금 거래 내역을 페이징 조회합니다.")
    @ApiResponse(responseCode = "200", description = "예치금 거래 내역 조회 성공")
    @ApiResponse(responseCode = "400", description = "페이지 파라미터가 올바르지 않습니다.")
    @GetMapping("/transactions")
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
    @PostMapping("/withdraw")
    public ResponseEntity<WalletWithdrawResponse> withdraw(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody WalletWithdrawRequest request) {

        WalletWithdrawResponse response = walletService.withdraw(userId, request);
        return ResponseEntity.ok(response);
    }

}