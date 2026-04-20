package com.devticket.settlement.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementServiceImpl settlementServiceImpl;

    // 공용 픽스처
    private final UUID sellerId = UUID.randomUUID();
    private final UUID settlementId = UUID.randomUUID();

    // ────────────────────────────────────────────────
    // previewSettlementTarget
    // ────────────────────────────────────────────────

    @Test
    void previewSettlementTarget_날짜지정_성공() throws Exception {
        LocalDate date = LocalDate.of(2024, 1, 1);
        SettlementTargetPreviewResponse response = makePreviewResponse("2024-01-01", 2, 2, 0);
        given(settlementServiceImpl.previewSettlementTarget(date)).willReturn(response);

        mockMvc.perform(get("/api/test/settlement-target/preview")
                .param("date", "2024-01-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetDate").value("2024-01-01"))
            .andExpect(jsonPath("$.totalEventCount").value(2))
            .andExpect(jsonPath("$.savedCount").value(2))
            .andExpect(jsonPath("$.skippedCount").value(0));
    }

    @Test
    void previewSettlementTarget_날짜미지정_어제날짜로_조회() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementTargetPreviewResponse response = makePreviewResponse(yesterday.toString(), 1, 1, 0);
        given(settlementServiceImpl.previewSettlementTarget(yesterday)).willReturn(response);

        mockMvc.perform(get("/api/test/settlement-target/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetDate").value(yesterday.toString()));
    }

    @Test
    void previewSettlementTarget_종료이벤트없음_빈응답() throws Exception {
        LocalDate date = LocalDate.of(2024, 1, 1);
        SettlementTargetPreviewResponse response = makePreviewResponse("2024-01-01", 0, 0, 0);
        given(settlementServiceImpl.previewSettlementTarget(date)).willReturn(response);

        mockMvc.perform(get("/api/test/settlement-target/preview")
                .param("date", "2024-01-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalEventCount").value(0))
            .andExpect(jsonPath("$.items").isEmpty());
    }

    // ────────────────────────────────────────────────
    // getSellerSettlements
    // ────────────────────────────────────────────────

    @Test
    void getSellerSettlements_성공() throws Exception {
        List<SettlementResponse> settlements = List.of(
            new SettlementResponse(settlementId, "2024-01-01", "2024-01-31",
                100000, 0, 3000, 97000, SettlementStatus.COMPLETED, "2024-02-01T10:00:00"),
            new SettlementResponse(UUID.randomUUID(), "2024-02-01", "2024-02-28",
                50000, 5000, 1500, 43500, SettlementStatus.PENDING, null)
        );
        given(settlementServiceImpl.getSellerSettlements(sellerId)).willReturn(settlements);

        mockMvc.perform(get("/api/seller/settlements")
                .header("X-User-Id", sellerId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].totalSalesAmount").value(100000))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    @Test
    void getSellerSettlements_빈목록_빈배열반환() throws Exception {
        given(settlementServiceImpl.getSellerSettlements(sellerId)).willReturn(List.of());

        mockMvc.perform(get("/api/seller/settlements")
                .header("X-User-Id", sellerId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ────────────────────────────────────────────────
    // getSellerSettlement (detail)
    // ────────────────────────────────────────────────

    @Test
    void getSellerSettlement_상세조회_성공() throws Exception {
        SellerSettlementDetailResponse detail = new SellerSettlementDetailResponse(
            settlementId.toString(), "2024-01-01T00:00:00", "2024-01-31T23:59:59",
            100000, 0, 3000, 97000, "COMPLETED", "2024-02-01T10:00:00", List.of()
        );
        given(settlementServiceImpl.getSellerSettlementDetail(sellerId, settlementId)).willReturn(detail);

        mockMvc.perform(get("/api/seller/settlements/{settlementId}", settlementId)
                .header("X-User-Id", sellerId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settlementId").value(settlementId.toString()))
            .andExpect(jsonPath("$.totalSalesAmount").value(100000))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getSellerSettlement_정산없음_400반환() throws Exception {
        given(settlementServiceImpl.getSellerSettlementDetail(any(UUID.class), eq(settlementId)))
            .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_BAD_REQUEST));

        mockMvc.perform(get("/api/seller/settlements/{settlementId}", settlementId)
                .header("X-User-Id", sellerId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SETTLEMENT_002"));
    }

    @Test
    void getSellerSettlement_다른판매자접근_403반환() throws Exception {
        UUID anotherSellerId = UUID.randomUUID();
        given(settlementServiceImpl.getSellerSettlementDetail(eq(anotherSellerId), eq(settlementId)))
            .willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/seller/settlements/{settlementId}", settlementId)
                .header("X-User-Id", anotherSellerId.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMMON_005"));
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