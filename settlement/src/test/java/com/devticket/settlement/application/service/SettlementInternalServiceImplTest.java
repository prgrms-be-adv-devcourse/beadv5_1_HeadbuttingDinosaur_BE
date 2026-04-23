package com.devticket.settlement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.domain.repository.FeePolicyRepository;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.client.SettlementToCommerceClient;
import com.devticket.settlement.infrastructure.client.SettlementToMemberClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementInternalServiceImplTest {

    @Mock private SettlementToCommerceClient settlementToCommerceClient;
    @Mock private SettlementToMemberClient settlementToMemberClient;
    @Mock private FeePolicyRepository feePolicyRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;

    @InjectMocks
    private SettlementInternalServiceImpl service;

    private final UUID sellerId = UUID.randomUUID();

    // ────────────────────────────────────────────────
    // createSettlementFromItems
    // ────────────────────────────────────────────────

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
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(10670); // 4850 + 5820
        assertThat(saved.getCarriedInAmount()).isEqualTo(5820);

        // 이월된 pending 정산서에 carriedToSettlementId 설정 확인
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
        assertThat(saved.getFinalSettlementAmount()).isEqualTo(7000); // 3000 + 4000
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

        verify(settlementRepository).save(any(Settlement.class));
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

    // ────────────────────────────────────────────────
    // 헬퍼
    // ────────────────────────────────────────────────

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
