package com.devticket.settlement.domain.repository;

import java.util.List;
import java.util.UUID;

import com.devticket.settlement.domain.model.Settlement;

public interface SettlementRepository {

    List<Settlement> findBySellerId(UUID sellerId);

}
