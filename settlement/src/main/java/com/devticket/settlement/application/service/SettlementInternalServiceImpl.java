package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.FeePolicy;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementInternalServiceImpl implements SettlementInternalService {

    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToMemberClient settlementToMemberClient;
    private final FeePolicyRepository feePolicyRepository;

    @Transactional(readOnly = true)
    public InternalSettlementPageResponse getSettlements(
        String status,
        UUID sellerId,
        String startDate,
        String endDate,
        Pageable pageable
    ) {
        List<UUID> sellerIds = resolveSellerIds(sellerId);
        List<InternalSettlementResponse> all = new ArrayList<>();
        FeePolicy feePolicy = feePolicyRepository.findTopByOrderByCreatedAtDesc()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.FEE_POLICY_NOT_FOUND));

        for (UUID currentSellerId : sellerIds) {
            InternalSettlementDataRequest request = new InternalSettlementDataRequest(
                currentSellerId,
                normalizeStartDate(startDate),
                normalizeEndDate(endDate)
            );

            InternalSettlementDataResponse data = settlementToCommerceClient.getSettlementData(request);

            if (data == null || data.eventSettlements() == null) {
                continue;
            }

            for (InternalSettlementDataResponse.EventSettlements eventSettlement : data.eventSettlements()) {
                long totalFeeAmount = feePolicy.calculateFee(eventSettlement.totalSalesAmount());
                long finalSettlementAmount = eventSettlement.totalSalesAmount()
                    - eventSettlement.totalRefundAmount()
                    - totalFeeAmount;

                InternalSettlementResponse row = new InternalSettlementResponse(
                    eventSettlement.eventId(),
                    data.periodStart(),
                    data.periodEnd(),
                    (long) eventSettlement.totalSalesAmount(),
                    (long) eventSettlement.totalRefundAmount(),
                    totalFeeAmount,              // ← 여기
                    finalSettlementAmount,
                    status == null || status.isBlank() ? "COMPLETED" : status,
                    null
                );

                all.add(row);
            }
        }

        List<InternalSettlementResponse> pagedContent = slice(all, pageable);
        int totalPages = pageable.getPageSize() <= 0 ? 0 :
            (int) Math.ceil((double) all.size() / pageable.getPageSize());

        return new InternalSettlementPageResponse(
            pagedContent,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            all.size(),
            totalPages
        );
    }

    @Transactional
    public void runSettlement() {
        List<UUID> sellerIds = settlementToMemberClient.getSellerIds();

        for (UUID sellerId : sellerIds) {
            InternalSettlementDataRequest request = new InternalSettlementDataRequest(
                sellerId,
                normalizeStartDate(null),
                normalizeEndDate(null)
            );

            InternalSettlementDataResponse data = settlementToCommerceClient.getSettlementData(request);

            // TODO:
            // 1. 정산 엔티티 저장
            // 2. 상태값 계산
            // 3. Kafka 이벤트 발행 or 후속 처리
        }
    }

    private List<UUID> resolveSellerIds(UUID sellerId) {
        if (sellerId != null) {
            return List.of(sellerId);
        }
        return settlementToMemberClient.getSellerIds();
    }

    private String normalizeStartDate(String startDate) {
        if (startDate != null && !startDate.isBlank()) {
            return startDate;
        }
        return LocalDate.now().withDayOfMonth(1).toString();
    }

    private String normalizeEndDate(String endDate) {
        if (endDate != null && !endDate.isBlank()) {
            return endDate;
        }
        return LocalDate.now().toString();
    }

    private List<InternalSettlementResponse> slice(
        List<InternalSettlementResponse> source,
        Pageable pageable
    ) {
        int start = (int) pageable.getOffset();
        if (start >= source.size()) {
            return List.of();
        }

        int end = Math.min(start + pageable.getPageSize(), source.size());
        return source.subList(start, end);
    }
}