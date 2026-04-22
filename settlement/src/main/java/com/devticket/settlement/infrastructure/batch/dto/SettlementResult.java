package com.devticket.settlement.infrastructure.batch.dto;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import java.util.List;

public record SettlementResult(
    Settlement settlement,
    Settlement pendingSettlement,
    List<SettlementItem> items
) {}