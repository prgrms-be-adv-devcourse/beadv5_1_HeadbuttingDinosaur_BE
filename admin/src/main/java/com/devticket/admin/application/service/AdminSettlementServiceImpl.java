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

    @Override
    @Transactional
    public void runSettlement(UUID adminId) {
        settlementInternalClient.runSettlement();      // 중복이면 409(SETTLEMENT_002) → 전역 핸들러 변환
        adminActionRepository.save(
            AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.SETTLEMENT)
                .targetId(adminId)                     // run은 특정 대상이 없으니 논의 필요
                .actionType(AdminActionType.FORCE_CANCEL_EVENT /* or 신규 RUN_SETTLEMENT */)
                .build()
        );
    }
}
