package com.devticket.settlement.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.devticket.settlement.application.service.SettlementServiceImpl;
import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.domain.exception.SettlementErrorCode;
import com.devticket.settlement.domain.model.SettlementStatus;
import com.devticket.settlement.presentation.dto.SellerSettlementDetailResponse;
import com.devticket.settlement.presentation.dto.SettlementResponse;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementServiceImpl settlementServiceImpl;

    @InjectMocks
    private SettlementController settlementController;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID settlementId = UUID.randomUUID();

    // ────────────────────────────────────────────────
    // previewSettlementTarget
    // ────────────────────────────────────────────────

    @Test
    void previewSettlementTarget_날짜지정_성공() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        SettlementTargetPreviewResponse response = makePreviewResponse("2024-01-01", 2, 2, 0);
        given(settlementServiceImpl.previewSettlementTarget(date)).willReturn(response);

        ResponseEntity<SettlementTargetPreviewResponse> result =
            settlementController.previewSettlementTarget(date);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().targetDate()).isEqualTo("2024-01-01");
        assertThat(result.getBody().totalEventCount()).isEqualTo(2);
        assertThat(result.getBody().savedCount()).isEqualTo(2);
        assertThat(result.getBody().skippedCount()).isEqualTo(0);
    }

    @Test
    void previewSettlementTarget_날짜미지정_어제날짜로_조회() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementTargetPreviewResponse response = makePreviewResponse(yesterday.toString(), 1, 1, 0);
        given(settlementServiceImpl.previewSettlementTarget(yesterday)).willReturn(response);

        ResponseEntity<SettlementTargetPreviewResponse> result =
            settlementController.previewSettlementTarget(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().targetDate()).isEqualTo(yesterday.toString());
    }

    @Test
    void previewSettlementTarget_종료이벤트없음_빈응답() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        SettlementTargetPreviewResponse response = makePreviewResponse("2024-01-01", 0, 0, 0);
        given(settlementServiceImpl.previewSettlementTarget(date)).willReturn(response);

        ResponseEntity<SettlementTargetPreviewResponse> result =
            settlementController.previewSettlementTarget(date);

        assertThat(result.getBody().totalEventCount()).isEqualTo(0);
        assertThat(result.getBody().items()).isEmpty();
    }

    // ────────────────────────────────────────────────
    // getSellerSettlements
    // ────────────────────────────────────────────────

    @Test
    void getSellerSettlements_성공() {
        List<SettlementResponse> settlements = List.of(
            new SettlementResponse(settlementId, "2024-01-01", "2024-01-31",
                100000, 0, 3000, 97000, SettlementStatus.COMPLETED, "2024-02-01T10:00:00"),
            new SettlementResponse(UUID.randomUUID(), "2024-02-01", "2024-02-28",
                50000, 5000, 1500, 43500, SettlementStatus.PENDING, null)
        );
        given(settlementServiceImpl.getSellerSettlements(sellerId)).willReturn(settlements);

        ResponseEntity<List<SettlementResponse>> result =
            settlementController.getSellerSettlements(sellerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).totalSalesAmount()).isEqualTo(100000);
        assertThat(result.getBody().get(0).status()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getBody().get(1).status()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    void getSellerSettlements_빈목록_빈배열반환() {
        given(settlementServiceImpl.getSellerSettlements(sellerId)).willReturn(List.of());

        ResponseEntity<List<SettlementResponse>> result =
            settlementController.getSellerSettlements(sellerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    // ────────────────────────────────────────────────
    // getSellerSettlement (detail)
    // ────────────────────────────────────────────────

    @Test
    void getSellerSettlement_상세조회_성공() {
        SellerSettlementDetailResponse detail = new SellerSettlementDetailResponse(
            settlementId.toString(), "2024-01-01T00:00:00", "2024-01-31T23:59:59",
            100000, 0, 3000, 97000, "COMPLETED", "2024-02-01T10:00:00", List.of()
        );
        given(settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId)).willReturn(detail);

        ResponseEntity<SellerSettlementDetailResponse> result =
            settlementController.getSellerSettlement(sellerId, settlementId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().settlementId()).isEqualTo(settlementId.toString());
        assertThat(result.getBody().totalSalesAmount()).isEqualTo(100000);
        assertThat(result.getBody().status()).isEqualTo("COMPLETED");
    }

    @Test
    void getSellerSettlement_정산없음_BusinessException발생() {
        given(settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId))
            .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        assertThatThrownBy(() -> settlementController.getSellerSettlement(sellerId, settlementId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));
    }

    @Test
    void getSellerSettlement_다른판매자접근_BusinessException발생() {
        UUID anotherSellerId = UUID.randomUUID();
        given(settlementServiceImpl.getSellerSettlementDetail(anotherSellerId, settlementId))
            .willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> settlementController.getSellerSettlement(anotherSellerId, settlementId))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    // ────────────────────────────────────────────────
    // 헬퍼
    // ────────────────────────────────────────────────

    private SettlementTargetPreviewResponse makePreviewResponse(
        String date, int totalEvents, int saved, int skipped) {
        return new SettlementTargetPreviewResponse(
            date, totalEvents, saved, skipped, "PLATFORM_FEE", "0.03", List.of()
        );
    }
}