package com.devticket.settlement.presentation.scheduler;

import com.devticket.settlement.infrastructure.batch.SettlementItemProcessor;
import com.devticket.settlement.infrastructure.batch.SettlementItemReader;
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

    private final Job settlementJob;
    private final SettlementItemReader settlementItemReader;
    private final SettlementItemProcessor settlementItemProcessor;
    private final org.springframework.batch.core.launch.JobLauncher jobLauncher;


    @Scheduled(cron = "0 0 0 1 * *")
    public void runSettlementJob() {
        try {
            // 1. Reader 초기화
            settlementItemReader.init();

            // 2. Job 파라미터 생성 (중복 실행 방지용 time만 필요)
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

            // 3. Job 실행
            jobLauncher.run(settlementJob, jobParameters);
            log.info("[SettlementScheduler] 정산 배치 실행 완료");

        } catch (Exception e) {
            log.error("[SettlementScheduler] 정산 배치 실행 실패: ", e);
        }
    }
}