package com.devticket.settlement.application.service;

import java.util.List;
import java.util.UUID;

import com.devticket.settlement.presentation.dto.SettlementResponse;

public interface SettlementService {

    List<SettlementResponse> getSellerSettlements(Long sellerId);

}
