 package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementInternalService;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class SettlementAdminController {

    private final SettlementInternalService settlementInternalService;

    @GetMapping
    public ResponseEntity<InternalSettlementPageResponse> getSettlements(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID sellerId,
        @RequestParam(required = false) String yearMonth,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
            settlementInternalService.getSettlements(status, sellerId, yearMonth, pageable)
        );
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<AdminSettlementDetailResponse> getSettlementDetail(
        @PathVariable UUID settlementId
    ) {
        return ResponseEntity.ok(settlementInternalService.getSettlementDetail(settlementId));
    }

    @PostMapping("/run")
    public ResponseEntity<Void> runSettlement() {
        //settlementInternalService.runSettlement();
        settlementInternalService.createSettlementFromItems();
        return ResponseEntity.ok().build();
    }

    // 정산기능 수동테스트용 API : SettlementItem데이터를 기반으로 Settelemnt생성
    @PostMapping("/create-from-items")
    public ResponseEntity<Void> createSettlementFromItems() {
        settlementInternalService.createSettlementFromItems();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{settlementId}/cancel")
    public ResponseEntity<Void> cancelSettlement(@PathVariable UUID settlementId) {
        settlementInternalService.cancelSettlement(settlementId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{settlementId}/payment")
    public ResponseEntity<Void> processPayment(@PathVariable UUID settlementId) {
        settlementInternalService.processPayment(settlementId);
        return ResponseEntity.ok().build();
    }
}
