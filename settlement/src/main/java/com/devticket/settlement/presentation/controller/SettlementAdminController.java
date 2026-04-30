 package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementAdminService;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.presentation.dto.MonthlyRevenueResponse;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

    private final SettlementAdminService settlementAdminService;

    @GetMapping
    public ResponseEntity<InternalSettlementPageResponse> getSettlements(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID sellerId,
        @RequestParam(required = false) String yearMonth,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
            settlementAdminService.getSettlements(status, sellerId, yearMonth, pageable)
        );
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<AdminSettlementDetailResponse> getSettlementDetail(
        @PathVariable UUID settlementId
    ) {
        return ResponseEntity.ok(settlementAdminService.getSettlementDetail(settlementId));
    }

    @PostMapping("/run")
    public ResponseEntity<Void> runSettlement() {
        //settlementInternalService.runSettlement();
        settlementAdminService.createSettlementFromItems();
        return ResponseEntity.ok().build();
    }

    // 정산기능 수동테스트용 API : SettlementItem데이터를 기반으로 Settelemnt생성
    @PostMapping("/create-from-items")
    public ResponseEntity<Void> createSettlementFromItems() {
        settlementAdminService.createSettlementFromItems();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{settlementId}/cancel")
    public ResponseEntity<Void> cancelSettlement(@PathVariable UUID settlementId) {
        settlementAdminService.cancelSettlement(settlementId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{settlementId}/payment")
    public ResponseEntity<Void> processPayment(@PathVariable UUID settlementId) {
        settlementAdminService.processPayment(settlementId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/revenues")
    public ResponseEntity<MonthlyRevenueResponse> getMonthlyRevenue(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth
    ) {
        return ResponseEntity.ok(settlementAdminService.getMonthlyRevenue(yearMonth));
    }
}
