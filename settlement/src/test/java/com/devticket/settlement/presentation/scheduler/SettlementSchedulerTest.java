package com.devticket.settlement.presentation.scheduler;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private SettlementScheduler settlementScheduler;

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

        // 예외가 스케줄러 밖으로 전파되지 않아야 함
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
}