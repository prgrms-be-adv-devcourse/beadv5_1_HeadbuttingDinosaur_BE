package com.devticket.settlement.application.service;

import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import java.util.List;
import java.util.UUID;

public interface SettlementService {

    InternalSettlementDataResponse fetchSettlementData(UUID sellerId, String periodStart, String periodEnd);

    List<SettlementResponse> getSellerSettlements(UUID sellerId);

    SellerSettlementDetailResponse getSellerSettlementDetail(UUID sellerId, UUID settlementId);
}
