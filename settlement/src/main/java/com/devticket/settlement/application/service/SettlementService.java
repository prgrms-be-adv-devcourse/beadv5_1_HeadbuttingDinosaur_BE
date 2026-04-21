package com.devticket.settlement.application.service;

import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementPeriodResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SettlementService {

    InternalSettlementDataResponse fetchSettlementData(UUID sellerId, String periodStart, String periodEnd);

    List<SettlementResponse> getSellerSettlements(UUID sellerId);

    SellerSettlementDetailResponse getSellerSettlementDetail(UUID sellerId, UUID settlementId);

    SettlementTargetPreviewResponse previewSettlementTarget(LocalDate targetDate);

    SettlementTargetPreviewResponse collectSettlementTargets(LocalDate targetDate);

    SettlementPeriodResponse getSettlementByPeriod(UUID sellerId, String yearMonth);
}
