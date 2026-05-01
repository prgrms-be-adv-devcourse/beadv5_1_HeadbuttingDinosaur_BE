package com.devticket.settlement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToEventClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import com.devticket.settlement.infrastructure.client.SettlementToPaymentClient;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.MonthlyRevenueResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementAdminServiceImplTest {

    @Mock private SettlementToCommerceClient settlementToCommerceClient;
    @Mock private SettlementToPaymentClient settlementToPaymentClient;
    @Mock private SettlementToMemberClient settlementToMemberClient;
    @Mock private SettlementToEventClient settlementToEventClient;
    @Mock private FeePolicyRepository feePolicyRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;

    @InjectMocks
    private SettlementAdminServiceImpl service;

    private final UUID sellerId = UUID.randomUUID();

    @Test
    void createSettlementFromItems_최소금액충족_CONFIRMED생성() {
        SettlementItem item = buildItem(sellerId, 48500L);
        givenReadyItems(List.of(item));
        givenNoPendingSettlements();
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        Settlement saved = captureNewSettlement();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(48500);
        assertThat(saved.getCarriedInAmount()).isEqualTo(0);
    }

    @Test
    void createSettlementFromItems_최소금액미달_PENDING_MIN_AMOUNT생성() {
        SettlementItem item = buildItem(sellerId, 4850L);
        givenReadyItems(List.of(item));
        givenNoPendingSettlements();
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        Settlement saved = captureNewSettlement();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING_MIN_AMOUNT);
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(4850);
        assertThat(saved.getCarriedInAmount()).isEqualTo(0);
    }

    @Test
    void createSettlementFromItems_이월합산으로_최소금액충족_CONFIRMED_이월처리() {
        SettlementItem item = buildItem(sellerId, 4850L);
        Settlement pending = buildPendingSettlement(sellerId, 5820);

        givenReadyItems(List.of(item));
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        givenNotAlreadySettled(sellerId);
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        Settlement saved = captureNewSettlement();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(10670);
        assertThat(saved.getCarriedInAmount()).isEqualTo(5820);

        assertThat(pending.getCarriedToSettlementId()).isEqualTo(saved.getSettlementId());
        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.PENDING_MIN_AMOUNT);
    }

    @Test
    void createSettlementFromItems_이월포함해도_최소금액미달_PENDING_MIN_AMOUNT() {
        SettlementItem item = buildItem(sellerId, 3000L);
        Settlement pending = buildPendingSettlement(sellerId, 4000);

        givenReadyItems(List.of(item));
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        givenNotAlreadySettled(sellerId);
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        Settlement saved = captureNewSettlement();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING_MIN_AMOUNT);
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(7000);
    }

    @Test
    void createSettlementFromItems_신규아이템없고_이월판매자만있으면_처리됨() {
        UUID carryOverSellerId = UUID.randomUUID();
        Settlement pending = buildPendingSettlement(carryOverSellerId, 3000);

        given(settlementItemRepository.findByStatusAndEventDateTimeBetween(
            eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(carryOverSellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        givenNotAlreadySettled(carryOverSellerId);
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        verify(settlementRepository, atLeastOnce()).save(any(Settlement.class));
    }

    @Test
    void createSettlementFromItems_아이템없고_이월없음_아무처리없음() {
        given(settlementItemRepository.findByStatusAndEventDateTimeBetween(
            eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());

        service.createSettlementFromItems();

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(settlementItemRepository, never()).saveAll(anyList());
    }

    @Test
    void createSettlementFromItems_동일기간_이미정산존재_스킵() {
        SettlementItem item = buildItem(sellerId, 48500L);
        givenReadyItems(List.of(item));
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());
        given(settlementRepository.existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(
            eq(sellerId), any(LocalDateTime.class), any(LocalDateTime.class), eq(SettlementStatus.CANCELLED)))
            .willReturn(true);

        service.createSettlementFromItems();

        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(settlementItemRepository, never()).saveAll(anyList());
    }

    @Test
    void createSettlementFromItems_정산완료시_아이템_FINALIZED처리() {
        SettlementItem item = buildItem(sellerId, 48500L);
        givenReadyItems(List.of(item));
        givenNoPendingSettlements();
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.FINALIZED);
        assertThat(item.getSettlementId()).isNotNull();
        verify(settlementItemRepository).saveAll(List.of(item));
    }

    @Test
    void createSettlementFromItems_신규아이템없는_이월판매자_아이템finalize미호출() {
        UUID carryOverSellerId = UUID.randomUUID();
        Settlement pending = buildPendingSettlement(carryOverSellerId, 3000);

        given(settlementItemRepository.findByStatusAndEventDateTimeBetween(
            eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(List.of());
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(carryOverSellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of(pending));
        givenNotAlreadySettled(carryOverSellerId);
        givenSaveReturnsArgument();

        service.createSettlementFromItems();

        verify(settlementItemRepository, never()).saveAll(anyList());
    }

    @Test
    void processPayment_CONFIRMED_지급성공_PAID처리() {
        Settlement settlement = buildConfirmedSettlement(sellerId, 18000);
        given(settlementRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(Optional.of(settlement));
        given(settlementRepository.findByCarriedToSettlementId(settlement.getSettlementId()))
            .willReturn(List.of());

        service.processPayment(settlement.getSettlementId());

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        verify(settlementToPaymentClient).transferToDeposit(
            settlement.getSettlementId(), settlement.getSellerId(), settlement.getFinalSettlementAmount()
        );
    }

    @Test
    void processPayment_지급성공_이월정산서도_PAID처리() {
        Settlement settlement = buildConfirmedSettlement(sellerId, 18000);
        Settlement carried = buildPendingSettlement(sellerId, 5000);

        given(settlementRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(Optional.of(settlement));
        given(settlementRepository.findByCarriedToSettlementId(settlement.getSettlementId()))
            .willReturn(List.of(carried));

        service.processPayment(settlement.getSettlementId());

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(carried.getStatus()).isEqualTo(SettlementStatus.PAID);
        verify(settlementRepository).saveAll(List.of(carried));
    }

    @Test
    void processPayment_지급실패_PAID_FAILED처리_예외전파() {
        Settlement settlement = buildConfirmedSettlement(sellerId, 18000);
        given(settlementRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(Optional.of(settlement));
        doThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR))
            .when(settlementToPaymentClient)
            .transferToDeposit(any(UUID.class), any(UUID.class), anyInt());

        assertThatThrownBy(() -> service.processPayment(settlement.getSettlementId()))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.PAYMENT_FAILED));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID_FAILED);
    }

    @Test
    void processPayment_PAID_FAILED_재시도_성공() {
        Settlement settlement = buildSettlementWithStatus(sellerId, 18000, SettlementStatus.PAID_FAILED);
        given(settlementRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(Optional.of(settlement));
        given(settlementRepository.findByCarriedToSettlementId(settlement.getSettlementId()))
            .willReturn(List.of());

        service.processPayment(settlement.getSettlementId());

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void processPayment_PENDING_MIN_AMOUNT상태_BAD_REQUEST예외() {
        Settlement settlement = buildPendingSettlement(sellerId, 5000);
        given(settlementRepository.findBySettlementId(settlement.getSettlementId()))
            .willReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.processPayment(settlement.getSettlementId()))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        verify(settlementToPaymentClient, never()).transferToDeposit(any(), any(), anyInt());
    }

    @Test
    void processPayment_존재하지않는_settlementId_BAD_REQUEST예외() {
        UUID unknownId = UUID.randomUUID();
        given(settlementRepository.findBySettlementId(unknownId))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.processPayment(unknownId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));
    }

    @Test
    void getSettlementDetail_eventTitle_벌크조회로_채워짐_그리고_eventId는_UUID() {
        UUID settlementId = UUID.randomUUID();
        UUID eventUUID1 = UUID.randomUUID();
        UUID eventUUID2 = UUID.randomUUID();

        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.of(2026, 3, 26, 0, 0))
            .periodEndAt(LocalDateTime.of(2026, 4, 25, 23, 59, 59))
            .totalSalesAmount(100000)
            .totalRefundAmount(0)
            .totalFeeAmount(3000)
            .finalSettlementAmount(97000)
            .carriedInAmount(0)
            .status(SettlementStatus.CONFIRMED)
            .build();

        SettlementItem item1 = SettlementItem.builder()
            .orderItemId(UUID.randomUUID())
            .eventId(1L).eventUUID(eventUUID1).sellerId(sellerId)
            .salesAmount(50000L).refundAmount(0L).feeAmount(1500L).settlementAmount(48500L)
            .status(SettlementItemStatus.READY).eventDateTime(LocalDate.now().minusDays(10))
            .build();
        SettlementItem item2 = SettlementItem.builder()
            .orderItemId(UUID.randomUUID())
            .eventId(2L).eventUUID(eventUUID2).sellerId(sellerId)
            .salesAmount(50000L).refundAmount(0L).feeAmount(1500L).settlementAmount(48500L)
            .status(SettlementItemStatus.READY).eventDateTime(LocalDate.now().minusDays(5))
            .build();

        given(settlementRepository.findBySettlementId(settlementId)).willReturn(Optional.of(settlement));
        given(settlementItemRepository.findBySettlementId(settlementId)).willReturn(List.of(item1, item2));
        given(settlementRepository.findByCarriedToSettlementId(settlementId)).willReturn(List.of());
        given(settlementToEventClient.getEventTitles(anyList()))
            .willReturn(Map.of(eventUUID1, "이벤트 A", eventUUID2, "이벤트 B"));

        AdminSettlementDetailResponse response = service.getSettlementDetail(settlementId);

        assertThat(response.settlementItems()).hasSize(2);
        assertThat(response.settlementItems())
            .extracting("eventTitle")
            .containsExactlyInAnyOrder("이벤트 A", "이벤트 B");
        assertThat(response.settlementItems())
            .extracting("eventId")
            .containsExactlyInAnyOrder(eventUUID1.toString(), eventUUID2.toString());
        verify(settlementToEventClient).getEventTitles(anyList());
    }

    @Test
    void getSettlementDetail_항목없음_Event서비스_호출안함() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.of(2026, 3, 26, 0, 0))
            .periodEndAt(LocalDateTime.of(2026, 4, 25, 23, 59, 59))
            .totalSalesAmount(0).totalRefundAmount(0).totalFeeAmount(0)
            .finalSettlementAmount(0).carriedInAmount(0)
            .status(SettlementStatus.PENDING_MIN_AMOUNT)
            .build();

        given(settlementRepository.findBySettlementId(settlementId)).willReturn(Optional.of(settlement));
        given(settlementItemRepository.findBySettlementId(settlementId)).willReturn(List.of());
        given(settlementRepository.findByCarriedToSettlementId(settlementId)).willReturn(List.of());

        AdminSettlementDetailResponse response = service.getSettlementDetail(settlementId);

        assertThat(response.settlementItems()).isEmpty();
        verify(settlementToEventClient, never()).getEventTitles(anyList());
    }

    @Test
    void getMonthlyRevenue_정상조회_수수료합산반환() {
        YearMonth yearMonth = YearMonth.of(2026, 4);
        given(settlementRepository.sumFeeAmountByPeriodStartAt(any(LocalDateTime.class), any(LocalDateTime.class)))
            .willReturn(150_000L);

        MonthlyRevenueResponse response = service.getMonthlyRevenue(yearMonth);

        assertThat(response.yearMonth()).isEqualTo("2026-04");
        assertThat(response.periodStartAt()).isEqualTo("2026-03-26");
        assertThat(response.periodEndAt()).isEqualTo("2026-04-25");
        assertThat(response.totalFeeAmount()).isEqualTo(150_000L);
    }

    @Test
    void getMonthlyRevenue_해당월_정산서없음_0반환() {
        YearMonth yearMonth = YearMonth.of(2026, 4);
        given(settlementRepository.sumFeeAmountByPeriodStartAt(any(LocalDateTime.class), any(LocalDateTime.class)))
            .willReturn(0L);

        MonthlyRevenueResponse response = service.getMonthlyRevenue(yearMonth);

        assertThat(response.totalFeeAmount()).isEqualTo(0L);
    }

    @Test
    void getMonthlyRevenue_periodStartAt_계산_검증() {
        YearMonth yearMonth = YearMonth.of(2026, 4);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        given(settlementRepository.sumFeeAmountByPeriodStartAt(any(LocalDateTime.class), any(LocalDateTime.class)))
            .willReturn(0L);

        service.getMonthlyRevenue(yearMonth);

        verify(settlementRepository).sumFeeAmountByPeriodStartAt(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 3, 26, 0, 0, 0));
        assertThat(toCaptor.getValue().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 26));
    }

    private SettlementItem buildItem(UUID sellerId, long settlementAmount) {
        return SettlementItem.builder()
            .orderItemId(UUID.randomUUID())
            .eventId(1L)
            .eventUUID(UUID.randomUUID())
            .sellerId(sellerId)
            .salesAmount(settlementAmount + 1500L)
            .refundAmount(0L)
            .feeAmount(1500L)
            .settlementAmount(settlementAmount)
            .status(SettlementItemStatus.READY)
            .eventDateTime(LocalDate.now().minusDays(10))
            .build();
    }

    private Settlement buildPendingSettlement(UUID sellerId, int finalSettlementAmount) {
        return Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.now().minusMonths(2))
            .periodEndAt(LocalDateTime.now().minusMonths(1))
            .totalSalesAmount(finalSettlementAmount + 150)
            .totalRefundAmount(0)
            .totalFeeAmount(150)
            .finalSettlementAmount(finalSettlementAmount)
            .carriedInAmount(0)
            .status(SettlementStatus.PENDING_MIN_AMOUNT)
            .settledAt(LocalDateTime.now().minusMonths(1))
            .build();
    }

    private Settlement buildConfirmedSettlement(UUID sellerId, int finalSettlementAmount) {
        return Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.now().minusMonths(1))
            .periodEndAt(LocalDateTime.now())
            .totalSalesAmount(finalSettlementAmount + 570)
            .totalRefundAmount(0)
            .totalFeeAmount(570)
            .finalSettlementAmount(finalSettlementAmount)
            .carriedInAmount(0)
            .status(SettlementStatus.CONFIRMED)
            .settledAt(LocalDateTime.now())
            .build();
    }

    private Settlement buildSettlementWithStatus(UUID sellerId, int finalSettlementAmount, SettlementStatus status) {
        return Settlement.builder()
            .sellerId(sellerId)
            .periodStartAt(LocalDateTime.now().minusMonths(1))
            .periodEndAt(LocalDateTime.now())
            .totalSalesAmount(finalSettlementAmount + 570)
            .totalRefundAmount(0)
            .totalFeeAmount(570)
            .finalSettlementAmount(finalSettlementAmount)
            .carriedInAmount(0)
            .status(status)
            .settledAt(LocalDateTime.now())
            .build();
    }

    private void givenReadyItems(List<SettlementItem> items) {
        given(settlementItemRepository.findByStatusAndEventDateTimeBetween(
            eq(SettlementItemStatus.READY), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(items);
    }

    private void givenNoPendingSettlements() {
        given(settlementRepository.findByStatus(SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());
        given(settlementRepository.findBySellerIdAndStatusAndCarriedToSettlementIdIsNull(sellerId, SettlementStatus.PENDING_MIN_AMOUNT))
            .willReturn(List.of());
        givenNotAlreadySettled(sellerId);
    }

    private void givenNotAlreadySettled(UUID sellerId) {
        given(settlementRepository.existsBySellerIdAndPeriodStartAtBetweenAndStatusNot(
            eq(sellerId), any(LocalDateTime.class), any(LocalDateTime.class), eq(SettlementStatus.CANCELLED)))
            .willReturn(false);
    }

    private void givenSaveReturnsArgument() {
        given(settlementRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private Settlement captureNewSettlement() {
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(0);
    }
}
