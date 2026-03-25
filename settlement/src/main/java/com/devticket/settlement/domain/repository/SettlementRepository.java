package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository {

    List<Settlement> findBySellerId(Long sellerId);

    Optional<SettlementItem> findById(Long id);

}
