package com.devticket.settlement.infrastructure.batch.dto;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerSettlementData(
    UUID sellerId,
    List<SettlementItem> items,
    Settlement pendingSettlement,
    LocalDateTime periodStart,
    LocalDateTime periodEnd
) {}