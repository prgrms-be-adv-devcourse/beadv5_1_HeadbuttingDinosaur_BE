package com.devticket.admin.application.service;

import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminSettelmentListResponse;
import java.util.UUID;

public interface AdminSettlementService {
    AdminSettelmentListResponse getSettlementList(AdminSettlementSearchRequest condition);
    void runSettlement(UUID adminId);
}
