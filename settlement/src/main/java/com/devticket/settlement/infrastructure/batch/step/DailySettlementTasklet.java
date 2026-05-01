package com.devticket.settlement.infrastructure.batch.step;

import com.devticket.settlement.application.service.SettlementService;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySettlementTasklet implements Tasklet {

    private final SettlementService settlementService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate targetDate = (LocalDate) chunkContext
            .getStepContext()
            .getJobParameters()
            .get("targetDate");

        log.info("[DailySettlementTasklet] 정산대상 데이터 수집 시작 - targetDate: {}", targetDate);
        SettlementTargetPreviewResponse result = settlementService.collectSettlementTargets(targetDate);
        log.info("[DailySettlementTasklet] 정산대상 데이터 수집 완료 - 이벤트: {}건, 저장: {}건, 스킵: {}건",
            result.totalEventCount(), result.savedCount(), result.skippedCount());

        return RepeatStatus.FINISHED;
    }
}