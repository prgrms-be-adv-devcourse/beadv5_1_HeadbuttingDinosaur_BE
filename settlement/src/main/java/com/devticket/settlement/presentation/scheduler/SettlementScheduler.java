package com.devticket.settlement.presentation.scheduler;

import com.devticket.settlement.application.service.SettlementService;
import com.devticket.settlement.infrastructure.batch.SettlementItemProcessor;
import com.devticket.settlement.infrastructure.batch.SettlementItemReader;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    @Scheduled(cron = "1 0 0 * * *")
    public void collectDailySettlementTargets() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[SettlementScheduler] 정산대상 데이터 수집 시작 - targetDate: {}", yesterday);
        try {
            SettlementTargetPreviewResponse result = settlementService.collectSettlementTargets(yesterday);
            log.info("[SettlementScheduler] 정산대상 데이터 수집 완료 - 이벤트: {}건, 저장: {}건, 스킵: {}건",
                result.totalEventCount(), result.savedCount(), result.skippedCount());
        } catch (Exception e) {
            //실패 시 에러 로그만 남겨 배치 스케줄러 자체는 계속 동작하도록 처리
            log.error("[SettlementScheduler] 정산대상 데이터 수집 실패 - targetDate: {}", yesterday, e);
        }
    }

}