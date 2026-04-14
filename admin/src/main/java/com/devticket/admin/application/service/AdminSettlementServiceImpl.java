package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.SettlementInternalClient;
import com.devticket.admin.infrastructure.external.dto.res.InternalSettlementPageResponse;
import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminSettelmentListResponse;
import com.devticket.admin.presentation.dto.res.SettlementResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementServiceImpl implements AdminSettlementService {

    private final SettlementInternalClient settlementInternalClient;
    private final AdminActionRepository adminActionRepository;

    @Override
    public AdminSettelmentListResponse getSettlementList(AdminSettlementSearchRequest condition) {
        InternalSettlementPageResponse page = settlementInternalClient.getSettlements(condition);
        List<SettlementResponse> content = page.content().stream()
            .map(s -> new SettlementResponse(
                s.settlementId(), s.periodStart(), s.periodEnd(),
                s.totalSalesAmount(), s.totalRefundAmount(), s.totalFeeAmount(),
                s.finalSettlementAmount(), s.status(), s.settledAt()))
            .toList();
        return new AdminSettelmentListResponse(content, page.page(), page.size(), page.totalElements(), page.totalPage());
    }

    @Transactional
    public void runSettlement(UUID adminId) {
        settlementInternalClient.runSettlement();

        adminActionRepository.save(
            AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.SETTLEMENT)
                .targetId(null)                          // 특정 대상 없음
                .actionType(AdminActionType.RUN_SETTLEMENT)
                .build()
        );
    }
}
