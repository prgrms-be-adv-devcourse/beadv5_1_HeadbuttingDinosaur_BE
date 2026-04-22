 package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementInternalService;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/settlements")
@RequiredArgsConstructor
public class InternalSettlementController {

    private final SettlementInternalService settlementInternalService;

    @GetMapping
    public ResponseEntity<InternalSettlementPageResponse> getSettlements(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID sellerId,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
            settlementInternalService.getSettlements(status, sellerId, startDate, endDate, pageable)
        );
    }

    @PostMapping("/run")
    public ResponseEntity<Void> runSettlement() {
        settlementInternalService.runSettlement();
        return ResponseEntity.ok().build();
    }

    // 정산기능 수동테스트용 API : SettlementItem데이터를 기반으로 Settelemnt생성
    @PostMapping("/create-from-items")
    public ResponseEntity<Void> createSettlementFromItems() {
        settlementInternalService.createSettlementFromItems();
        return ResponseEntity.ok().build();
    }
}
