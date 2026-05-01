package com.devticket.settlement.application.service;

import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.presentation.dto.MonthlyRevenueResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface SettlementAdminService {

    InternalSettlementPageResponse getSettlements(
        String status,
        UUID sellerId,
        String yearMonth,
        Pageable pageable
    );

    void runSettlement();

    void createSettlementFromItems();

    AdminSettlementDetailResponse getSettlementDetail(UUID settlementId);

    void cancelSettlement(UUID settlementId);

    void processPayment(UUID settlementId);

    MonthlyRevenueResponse getMonthlyRevenue(YearMonth yearMonth);
}
