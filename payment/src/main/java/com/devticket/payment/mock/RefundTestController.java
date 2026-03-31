package com.devticket.payment.mock;

import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/refunds")
@RequiredArgsConstructor
@Profile("local")
public class RefundTestController {

    private final RefundService refundService;

    private static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @GetMapping
    public ResponseEntity<RefundInfoResponse> getRefundInfo(@RequestParam String ticketId) {
        RefundInfoResponse response = refundService.getRefundInfo(TEST_USER_ID, ticketId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pg/{ticketId}")
    public ResponseEntity<PgRefundResponse> refundPgTicket(
        @PathVariable String ticketId,
        @Valid @RequestBody PgRefundRequest request
    ) {
        PgRefundResponse response = refundService.refundPgTicket(TEST_USER_ID, ticketId, request);
        return ResponseEntity.ok(response);
    }
}
