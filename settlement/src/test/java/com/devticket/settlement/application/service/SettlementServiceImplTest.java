package com.devticket.settlement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToEventClient;
import com.devticket.settlement.presentation.dto.SettlementPeriodResponse;
import java.time.LocalDate;
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
class SettlementServiceImplTest {

    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;
    @Mock private FeePolicyRepository feePolicyRepository;
    @Mock private SettlementToCommerceClient settlementToCommerceClient;
    @Mock private SettlementToEventClient settlementToEventClient;

    @InjectMocks
    private SettlementServiceImpl settlementServiceImpl;

    private final UUID sellerId = UUID.randomUUID();

    // ────────────────────────────────────────────────
    // getSettlementByPeriod — 입력값 검증
    // ────────────────────────────────────────────────

    @Test
    void getSettlementByPeriod_월값00_400예외() {
        assertThatThrownBy(() -> settlementServiceImpl.getSettlementByPeriod(sellerId, "202600"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void getSettlementByPeriod_월값13_400예외() {
        assertThatThrownBy(() -> settlementServiceImpl.getSettlementByPeriod(sellerId, "202613"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void getSettlementByPeriod_숫자아닌입력_400예외() {
        assertThatThrownBy(() -> settlementServiceImpl.getSettlementByPeriod(sellerId, "20261a"))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    // ────────────────────────────────────────────────
    // getSettlementByPeriod — 정상 조회
    // ────────────────────────────────────────────────

    @Test
    void getSettlementByPeriod_정산존재_응답반환() {
        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.of(2026, 2, 26, 0, 0))
            .periodEndAt(LocalDateTime.of(2026, 3, 25, 23, 59, 59))
            .totalSalesAmount(100000)
            .totalRefundAmount(0)
            .totalFeeAmount(3000)
            .finalSettlementAmount(97000)
            .carriedInAmount(0)
            .status(SettlementStatus.CONFIRMED)
            .settledAt(LocalDateTime.of(2026, 4, 1, 0, 10))
            .build();

        given(settlementRepository.findBySellerIdAndPeriodStartAtBetween(
            eq(sellerId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .willReturn(Optional.of(settlement));
        given(settlementItemRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(List.of());

        SettlementPeriodResponse result = settlementServiceImpl.getSettlementByPeriod(sellerId, "202603");

        assertThat(result.finalSettlementAmount()).isEqualTo(97000);
        assertThat(result.totalSalesAmount()).isEqualTo(100000);
        assertThat(result.totalFeeAmount()).isEqualTo(3000);
        assertThat(result.carriedInAmount()).isEqualTo(0);
        assertThat(result.settlementItems()).isEmpty();
    }

    @Test
    void getSettlementByPeriod_정산없음_빈응답반환() {
        given(settlementRepository.findBySellerIdAndPeriodStartAtBetween(
            eq(sellerId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .willReturn(Optional.empty());

        SettlementPeriodResponse result = settlementServiceImpl.getSettlementByPeriod(sellerId, "202603");

        assertThat(result.finalSettlementAmount()).isEqualTo(0);
        assertThat(result.totalSalesAmount()).isEqualTo(0);
        assertThat(result.totalFeeAmount()).isEqualTo(0);
        assertThat(result.settlementItems()).isEmpty();
    }

    // ────────────────────────────────────────────────
    // getSettlementPreview — 집계 검증
    // ────────────────────────────────────────────────

    @Test
    void getSettlementPreview_READY항목집계() {
        SettlementItem item = SettlementItem.builder()
            .orderItemId(UUID.randomUUID())
            .eventId(1L)
            .eventUUID(UUID.randomUUID())
            .sellerId(sellerId)
            .salesAmount(50000L)
            .refundAmount(0L)
            .feeAmount(1500L)
            .settlementAmount(48500L)
            .status(SettlementItemStatus.READY)
            .eventDateTime(LocalDate.now().minusDays(10))
            .build();

        given(settlementItemRepository.findBySellerIdAndStatusAndEventDateTimeBetween(
            eq(sellerId), eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of(item));
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());

        SettlementPeriodResponse result = settlementServiceImpl.getSettlementPreview(sellerId);

        assertThat(result.totalSalesAmount()).isEqualTo(50000);
        assertThat(result.totalFeeAmount()).isEqualTo(1500);
        assertThat(result.finalSettlementAmount()).isEqualTo(48500);
        assertThat(result.carriedInAmount()).isEqualTo(0);
        assertThat(result.settlementItems()).hasSize(1);
    }

    @Test
    void getSettlementPreview_이월금액합산() {
        Settlement pending = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.of(2026, 1, 26, 0, 0))
            .periodEndAt(LocalDateTime.of(2026, 2, 25, 23, 59, 59))
            .totalSalesAmount(5000)
            .totalRefundAmount(0)
            .totalFeeAmount(150)
            .finalSettlementAmount(4850)
            .carriedInAmount(0)
            .status(SettlementStatus.PENDING_MIN_AMOUNT)
            .settledAt(LocalDateTime.of(2026, 3, 1, 0, 10))
            .build();

        given(settlementItemRepository.findBySellerIdAndStatusAndEventDateTimeBetween(
            eq(sellerId), eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));

        SettlementPeriodResponse result = settlementServiceImpl.getSettlementPreview(sellerId);

        assertThat(result.carriedInAmount()).isEqualTo(4850);
        assertThat(result.finalSettlementAmount()).isEqualTo(4850);
    }

    @Test
    void getSettlementPreview_데이터없음_빈응답() {
        given(settlementItemRepository.findBySellerIdAndStatusAndEventDateTimeBetween(
            eq(sellerId), eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());

        SettlementPeriodResponse result = settlementServiceImpl.getSettlementPreview(sellerId);

        assertThat(result.finalSettlementAmount()).isEqualTo(0);
        assertThat(result.totalSalesAmount()).isEqualTo(0);
        assertThat(result.carriedInAmount()).isEqualTo(0);
        assertThat(result.settlementItems()).isEmpty();
    }
}
