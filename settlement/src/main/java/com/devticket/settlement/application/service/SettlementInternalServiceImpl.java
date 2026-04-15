package com.devticket.settlement.application.service;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.FeePolicy;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.dto.req.InternalSettlementDataRequest;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementInternalServiceImpl implements SettlementInternalService {

    private final SettlementToCommerceClient settlementToCommerceClient;
    private final SettlementToMemberClient settlementToMemberClient;
    private final FeePolicyRepository feePolicyRepository;
    private final SettlementRepository settlementRepository;

    @Transactional(readOnly = true)
    public InternalSettlementPageResponse getSettlements(
        String status,
        UUID sellerId,
        String startDate,
        String endDate,
        Pageable pageable
    ) {
        SettlementStatus filterStatus = parseStatus(status);
        LocalDateTime start = startDate != null && !startDate.isBlank()
            ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null && !endDate.isBlank()
            ? LocalDate.parse(endDate).plusDays(1).atStartOfDay() : null;

        Page<Settlement> page = settlementRepository.search(
            filterStatus, sellerId, start, end, pageable
        );

        List<InternalSettlementResponse> content = page.getContent().stream()
            .map(s -> new InternalSettlementResponse(
                null,
                s.getPeriodStartAt().toLocalDate().toString(),
                s.getPeriodEndAt().toLocalDate().toString(),
                (long) s.getTotalSalesAmount(),
                (long) s.getTotalRefundAmount(),
                (long) s.getTotalFeeAmount(),
                (long) s.getFinalSettlementAmount(),
                s.getStatus().name(),
                s.getSettledAt().toString()
            ))
            .toList();

        return new InternalSettlementPageResponse(
            content,
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Transactional
    public void runSettlement() {
        List<UUID> sellerIds = settlementToMemberClient.getSellerIds();
        FeePolicy feePolicy = feePolicyRepository.findTopByOrderByCreatedAtDesc()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.FEE_POLICY_NOT_FOUND));

        String periodStart = normalizeStartDate(null);
        String periodEnd = normalizeEndDate(null);

        for (UUID sellerId : sellerIds) {
            try {
                InternalSettlementDataRequest request = new InternalSettlementDataRequest(
                    sellerId, periodStart, periodEnd
                );

                InternalSettlementDataResponse data = settlementToCommerceClient.getSettlementData(request);
                if (data == null || data.eventSettlements() == null) continue;

                for (var eventSettlement : data.eventSettlements()) {
                    long fee = feePolicy.calculateFee(eventSettlement.totalSalesAmount());
                    long finalAmount = eventSettlement.totalSalesAmount()
                        - eventSettlement.totalRefundAmount() - fee;

                    Settlement settlement = Settlement.builder()
                        .sellerId(sellerId)
                        .periodStartAt(LocalDate.parse(periodStart).atStartOfDay())
                        .periodEndAt(LocalDate.parse(periodEnd).atStartOfDay())
                        .totalSalesAmount(eventSettlement.totalSalesAmount())
                        .totalRefundAmount(eventSettlement.totalRefundAmount())
                        .totalFeeAmount((int) fee)
                        .finalSettlementAmount((int) finalAmount)
                        .status(SettlementStatus.COMPLETED)
                        .settledAt(LocalDateTime.now())
                        .build();

                    settlementRepository.save(settlement);
                }
            } catch (Exception e) {
                log.warn("[정산 실패] sellerId: {}", sellerId, e);
            }
        }
    }

    private SettlementStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return SettlementStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeStartDate(String startDate) {
        if (startDate != null && !startDate.isBlank()) return startDate;
        return LocalDate.now().withDayOfMonth(1).toString();
    }

    private String normalizeEndDate(String endDate) {
        if (endDate != null && !endDate.isBlank()) return endDate;
        return LocalDate.now().toString();
    }
}