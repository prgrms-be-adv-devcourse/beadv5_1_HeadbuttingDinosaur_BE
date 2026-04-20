package com.devticket.settlement.domain.repository;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository {

    List<SettlementItem> findBySettlementId(UUID settlementId);

    boolean existsByOrderItemId(UUID orderItemId);

    SettlementItem save(SettlementItem settlementItem);

}
