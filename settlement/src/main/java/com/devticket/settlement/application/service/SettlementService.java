package com.devticket.settlement.application.service;

import com.devticket.settlement.presentation.dto.SettlementResponse;
import java.util.List;

public interface SettlementService {

    List<SettlementResponse> getSellerSettlements(Long sellerId);

    
}
