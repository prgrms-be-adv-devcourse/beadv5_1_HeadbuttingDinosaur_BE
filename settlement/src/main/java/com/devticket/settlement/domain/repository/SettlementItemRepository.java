package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.SettlementItem;
import java.util.List;
import java.util.UUID;

public interface SettlementItemRepository {

    List<SettlementItem> findBySettlementId(UUID settlementId);

}
