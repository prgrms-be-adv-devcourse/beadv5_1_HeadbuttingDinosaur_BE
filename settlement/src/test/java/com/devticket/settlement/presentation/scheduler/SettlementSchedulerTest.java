package com.devticket.settlement.presentation.scheduler;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.devticket.settlement.application.service.SettlementAdminService;
import com.devticket.settlement.application.service.SettlementService;
import com.devticket.settlement.common.exception.BusinessException;
import com.devticket.settlement.common.exception.CommonErrorCode;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementSchedulerTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private SettlementAdminService settlementAdminService;

    @InjectMocks
    private SettlementScheduler settlementScheduler;

    // ────────────────────────────────────────────────
    // collectDailySettlementTargets
    // ────────────────────────────────────────────────

    @Test
    void collectDailySettlementTargets_성공_어제날짜로_서비스호출() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementTargetPreviewResponse response = new SettlementTargetPreviewResponse(
            yesterday.toString(), 3, 3, 0, "PLATFORM_FEE", "0.03", List.of()
        );
        given(settlementService.collectSettlementTargets(yesterday)).willReturn(response);

        settlementScheduler.collectDailySettlementTargets();

        verify(settlementService).collectSettlementTargets(yesterday);
    }

    @Test
    void collectDailySettlementTargets_성공_저장및스킵_건수확인() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementTargetPreviewResponse response = new SettlementTargetPreviewResponse(
            yesterday.toString(), 5, 3, 2, "PLATFORM_FEE", "0.03", List.of()
        );
        given(settlementService.collectSettlementTargets(yesterday)).willReturn(response);

        settlementScheduler.collectDailySettlementTargets();

        verify(settlementService).collectSettlementTargets(yesterday);
    }

    @Test
    void collectDailySettlementTargets_서비스예외발생_예외를_밖으로_전파하지않음() {
        given(settlementService.collectSettlementTargets(any(LocalDate.class)))
            .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR));

        assertThatNoException().isThrownBy(
            () -> settlementScheduler.collectDailySettlementTargets()
        );
    }

    @Test
    void collectDailySettlementTargets_종료이벤트없음_정상완료() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SettlementTargetPreviewResponse emptyResponse = new SettlementTargetPreviewResponse(
            yesterday.toString(), 0, 0, 0, "PLATFORM_FEE", "0.03", List.of()
        );
        given(settlementService.collectSettlementTargets(yesterday)).willReturn(emptyResponse);

        settlementScheduler.collectDailySettlementTargets();

        verify(settlementService).collectSettlementTargets(yesterday);
    }

    // ────────────────────────────────────────────────
    // createMonthlySettlement
    // ────────────────────────────────────────────────

    @Test
    void createMonthlySettlement_성공_서비스호출() {
        willDoNothing().given(settlementAdminService).createSettlementFromItems();

        settlementScheduler.createMonthlySettlement();

        verify(settlementAdminService).createSettlementFromItems();
    }

    @Test
    void createMonthlySettlement_예외발생_밖으로_전파하지않음() {
        willThrow(new RuntimeException("정산 생성 실패"))
            .given(settlementAdminService).createSettlementFromItems();

        assertThatNoException().isThrownBy(
            () -> settlementScheduler.createMonthlySettlement()
        );
    }

    @Test
    void createMonthlySettlement_비즈니스예외발생_밖으로_전파하지않음() {
        willThrow(new BusinessException(CommonErrorCode.EXTERNAL_SERVICE_ERROR))
            .given(settlementAdminService).createSettlementFromItems();

        assertThatNoException().isThrownBy(
            () -> settlementScheduler.createMonthlySettlement()
        );
    }
}
