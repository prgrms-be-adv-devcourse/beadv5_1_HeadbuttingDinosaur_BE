package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.res.InternalSettlementPageResponse;
import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;

public interface SettlementInternalClient {
    InternalSettlementPageResponse getSettlements(AdminSettlementSearchRequest condition);
    void runSettlement();
}
