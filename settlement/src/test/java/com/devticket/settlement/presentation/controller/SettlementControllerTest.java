package com.devticket.settlement.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.devticket.settlement.application.service.SettlementServiceImpl;
import com.devticket.settlement.presentation.dto.EventItemResponse;
import com.devticket.settlement.presentation.dto.SettlementPeriodResponse;
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
    // getSettlementByPeriod
    // ────────────────────────────────────────────────

    @Test
    void getSettlementByPeriod_정상조회_성공() {
        String yearMonth = "202603";
        EventItemResponse item = new EventItemResponse("eventId", "이벤트", 100000L, 0L, 3000L, 97000L);
        SettlementPeriodResponse response = new SettlementPeriodResponse(97000, 3000, 100000, 0, List.of(item));
        given(settlementServiceImpl.getSettlementByPeriod(sellerId, yearMonth)).willReturn(response);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementByPeriod(sellerId, yearMonth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(97000);
        assertThat(result.getBody().totalSalesAmount()).isEqualTo(100000);
        assertThat(result.getBody().totalFeeAmount()).isEqualTo(3000);
        assertThat(result.getBody().carriedInAmount()).isEqualTo(0);
        assertThat(result.getBody().settlementItems()).hasSize(1);
    }

    @Test
    void getSettlementByPeriod_이월금액포함_성공() {
        String yearMonth = "202603";
        SettlementPeriodResponse response = new SettlementPeriodResponse(25000, 1500, 50000, 20000, List.of());
        given(settlementServiceImpl.getSettlementByPeriod(sellerId, yearMonth)).willReturn(response);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementByPeriod(sellerId, yearMonth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().carriedInAmount()).isEqualTo(20000);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(25000);
    }

    @Test
    void getSettlementByPeriod_정산내역없음_빈응답반환() {
        String yearMonth = "202601";
        SettlementPeriodResponse emptyResponse = new SettlementPeriodResponse(0, 0, 0, 0, List.of());
        given(settlementServiceImpl.getSettlementByPeriod(sellerId, yearMonth)).willReturn(emptyResponse);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementByPeriod(sellerId, yearMonth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(0);
        assertThat(result.getBody().settlementItems()).isEmpty();
    }

    // ────────────────────────────────────────────────
    // getSettlementPreview
    // ────────────────────────────────────────────────

    @Test
    void getSettlementPreview_READY항목있음_집계응답() {
        EventItemResponse item = new EventItemResponse("eventId", "이벤트", 50000L, 0L, 1500L, 48500L);
        SettlementPeriodResponse response = new SettlementPeriodResponse(48500, 1500, 50000, 0, List.of(item));
        given(settlementServiceImpl.getSettlementPreview(sellerId)).willReturn(response);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementPreview(sellerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(48500);
        assertThat(result.getBody().settlementItems()).hasSize(1);
    }

    @Test
    void getSettlementPreview_이월금액포함_합산응답() {
        SettlementPeriodResponse response = new SettlementPeriodResponse(18000, 600, 20000, 10000, List.of());
        given(settlementServiceImpl.getSettlementPreview(sellerId)).willReturn(response);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementPreview(sellerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().carriedInAmount()).isEqualTo(10000);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(18000);
    }

    @Test
    void getSettlementPreview_데이터없음_빈응답() {
        SettlementPeriodResponse emptyResponse = new SettlementPeriodResponse(0, 0, 0, 0, List.of());
        given(settlementServiceImpl.getSettlementPreview(sellerId)).willReturn(emptyResponse);

        ResponseEntity<SettlementPeriodResponse> result =
            settlementController.getSettlementPreview(sellerId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().finalSettlementAmount()).isEqualTo(0);
        assertThat(result.getBody().settlementItems()).isEmpty();
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
