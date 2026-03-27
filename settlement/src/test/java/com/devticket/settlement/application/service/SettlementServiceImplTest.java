package com.devticket.settlement.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class SettlementServiceImplTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementItemRepository settlementItemRepository;

    @InjectMocks
    private SettlementServiceImpl settlementServiceImpl;

    // 공용  Mock 데이터
    Long sellerId = 1L;
    UUID settlementId = UUID.randomUUID();

    // Mock Settlement 생성 메서드
    private Settlement makeSettlement(Long sellerId) {
        return Settlement.builder()
            .sellerId(sellerId)
            .settlementId(settlementId)
            .periodStartAt(LocalDateTime.of(2025, 1, 1, 0, 0))
            .periodEndAt(LocalDateTime.of(2025, 1, 2, 0, 0))
            .totalSalesAmount(100000)
            .totalRefundAmount(0)
            .totalFeeAmount(3000)
            .build();
    }

    // Mock SettlementItem 생성 메서드
    private List<SettlementItem> makeSettlementItems(UUID settlementId) {
        SettlementItem item = SettlementItem.builder()
            .settlementId(settlementId)
            .orderItemId(1L)
            .eventId(100L)
            .salesAmount(100000)
            .refundAmount(0)
            .feeAmount(3000)
            .settlementAmount(97000)
            .build();

        return List.of(item);
    }


    // 1. 정산 목록 조회 성공 케이스
    @Test
    void getSettlementListSuccess() {
        Settlement settlement1 = makeSettlement(sellerId);
        Settlement settlement2 = makeSettlement(sellerId);

        given(settlementRepository.findBySellerId(sellerId))
            .willReturn(List.of(settlement1, settlement2));

        List<SettlementResponse> result = settlementServiceImpl.getSellerSettlements(sellerId);

        // 1. 반환 갯수 검증
        assertThat(result).hasSize(2);
        // 2. DTO 변환 검증
        assertThat(result.get(0).totalSalesAmount()).isEqualTo(100000);
        // 3. ENUM 변환 검증
        assertThat(result.get(0).status()).isEqualTo(SettlementStatus.PENDING);
    }

    // 2. 정산 목록 조회 실패 케이스
    @Test
    void getSettlementListFail() {
        given(settlementRepository.findBySellerId(sellerId))
            .willReturn(List.of());

        // 예외 던지기 검증
        assertThatThrownBy(() -> settlementServiceImpl.getSellerSettlements(sellerId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> {
                BusinessException be = (BusinessException) e;
                assertThat(be.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
            });
    }

    // 3. 정산 내역 상세 조회 성공 케이스
    @Test
    void getSellerSettlementDetailSuccess() {
        Settlement settlement = makeSettlement(sellerId);
        List<SettlementItem> items = makeSettlementItems(settlementId);

        given(settlementRepository.findBySettlementId(settlementId))
            .willReturn(Optional.of(settlement));
        given(settlementItemRepository.findBySettlementId(settlementId))
            .willReturn(items);

        SellerSettlementDetailResponse result = settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId);

        // 1. null 여부 체크
        assertThat(result).isNotNull();

        // 2. DTO 변환 체크
        assertThat(result.totalSalesAmount()).isEqualTo(100000);
    }

    // 4. 정산 상세 조회 실패(1) - 정산 없음
    @Test
    void getSellerSettlementDetailFail_NotFound() {
        given(settlementRepository.findBySettlementId(settlementId))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> {
                BusinessException be = (BusinessException) e;
                assertThat(be.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_BAD_REQUEST);
            });
    }

    // 5. 정산 상세 조회 실패(2) - 다른 판매자 접근
    @Test
    void getSellerSettlementDetailFail_AccessDenied() {
        Long anotherSellerId = 999L;

        Settlement settlement = makeSettlement(sellerId);

        given(settlementRepository.findBySettlementId(settlementId))
            .willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> settlementServiceImpl.getSellerSettlementDetail(anotherSellerId, settlementId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> {
                BusinessException be = (BusinessException) e;
                assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
            });
    }
    
}
