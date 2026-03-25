package com.devticket.settlement.application.service;

import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import java.util.List;
import java.util.UUID;

public interface SettlementService {

    List<SettlementResponse> getSellerSettlements(Long sellerId);

    SellerSettlementDetailResponse getSellerSettlementDetail(Long sellerId, UUID settlementId);
}
