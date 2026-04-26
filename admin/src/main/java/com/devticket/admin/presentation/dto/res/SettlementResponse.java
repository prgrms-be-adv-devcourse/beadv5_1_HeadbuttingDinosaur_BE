package com.devticket.admin.presentation.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record SettlementResponse(

    Long settlementId,
    LocalDateTime periodStart,
    LocalDateTime periodEnd,
    Long totalSalesAmount,
    Long totalRefundAmount,
    Long totalFeeAmount,
    Long finalSettlementAmount,
    String status,
    LocalDateTime settledAt

) {


}
