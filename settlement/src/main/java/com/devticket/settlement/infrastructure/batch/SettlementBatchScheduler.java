package com.devticket.settlement.infrastructure.batch;

import java.time.LocalDate;
import java.time.YearMonth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SettlementBatchScheduler {

    private final JobOperator jobOperator;
    private final Job dailySettlementJob;
    private final Job monthlySettlementJob;

    public SettlementBatchScheduler(
        JobOperator jobOperator,
        @Qualifier("dailySettlementJob") Job dailySettlementJob,
        @Qualifier("monthlySettlementJob") Job monthlySettlementJob
    ) {
        this.jobOperator = jobOperator;
        this.dailySettlementJob = dailySettlementJob;
        this.monthlySettlementJob = monthlySettlementJob;
    }

    // 매일 00:01 실행 - 전일 정산대상 데이터 수집
    @Scheduled(cron = "0 1 0 * * *")
    public void launchDailySettlementJob() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("[SettlementBatchScheduler] 일별 정산대상 수집 배치 시작 - targetDate: {}", targetDate);
        try {
            JobParameters params = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
            jobOperator.start(dailySettlementJob, params);
        } catch (Exception e) {
            log.error("[SettlementBatchScheduler] 일별 정산대상 수집 배치 실패", e);
        }
    }

    // 매월 1일 00:10 실행 - 전월 정산서 생성
    @Scheduled(cron = "0 10 0 1 * *")
    public void launchMonthlySettlementJob() {
        String yearMonth = YearMonth.now().minusMonths(1).toString();
        log.info("[SettlementBatchScheduler] 월 정산 배치 시작 - yearMonth: {}", yearMonth);
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("yearMonth", yearMonth)
                .toJobParameters();
            jobOperator.start(monthlySettlementJob, params);
        } catch (Exception e) {
            log.error("[SettlementBatchScheduler] 월 정산 배치 실패", e);
        }
    }
}