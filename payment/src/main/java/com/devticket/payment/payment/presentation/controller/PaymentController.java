package com.devticket.payment.payment.presentation.controller;

import com.devticket.payment.payment.application.service.PaymentService;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 준비", description = "결제 수단에 따라 예치금 즉시 결제 또는 PG 결제 준비를 수행합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "결제 준비 완료 (PG) 또는 결제 성공 (WALLET)"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 결제 요청"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 주문"),
        @ApiResponse(responseCode = "409", description = "이미 처리된 결제 또는 예치금 부족")
    })
    @PostMapping("/ready")
    public ResponseEntity<PaymentReadyResponse> readyPayment(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody PaymentReadyRequest request
    ) {
        PaymentReadyResponse response = paymentService.readyPayment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "PG 결제 승인",
        description = "클라이언트가 전달한 paymentKey, orderId, amount를 기반으로 PG 결제 승인을 처리합니다."
    )
    @ApiResponse(responseCode = "200", description = "PG 결제 승인 성공")
    @ApiResponse(
        responseCode = "400",
        description = "유효하지 않은 요청",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "502",
        description = "PG 결제 승인 실패",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "503",
        description = "PG 응답 지연 또는 네트워크 오류",
        content = @Content(schema = @Schema(hidden = true))
    )
    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
        @RequestHeader("X-User-Id") UUID userId,
        @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentConfirmResponse response = paymentService.confirmPgPayment(userId, request);
        return ResponseEntity.ok(response);
    }
}