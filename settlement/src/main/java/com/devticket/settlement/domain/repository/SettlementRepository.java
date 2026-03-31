package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.Settlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository {

    List<Settlement> findBySellerId(UUID sellerId);

    Optional<Settlement> findBySettlementId(UUID settlementId);

}
