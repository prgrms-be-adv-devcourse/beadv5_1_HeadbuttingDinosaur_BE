// 스프링 스케줄링 -> 스프링 배치 방식으로 교체

//package com.devticket.settlement.presentation.scheduler;
//
//import java.time.LocalDate;
//import java.time.YearMonth;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.batch.core.job.Job;
//import org.springframework.batch.core.job.parameters.JobParameters;
//import org.springframework.batch.core.job.parameters.JobParametersBuilder;
//import org.springframework.batch.core.launch.JobOperator;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//public class SettlementScheduler {
//
//    private final JobOperator jobOperator;
//    private final Job dailySettlementJob;
//    private final Job monthlySettlementJob;
//
//    public SettlementScheduler(
//        JobOperator jobOperator,
//        @Qualifier("dailySettlementJob") Job dailySettlementJob,
//        @Qualifier("monthlySettlementJob") Job monthlySettlementJob
//    ) {
//        this.jobOperator = jobOperator;
//        this.dailySettlementJob = dailySettlementJob;
//        this.monthlySettlementJob = monthlySettlementJob;
//    }
//
//    // 매일 10:00 실행
//    @Scheduled(cron = "0 0 10 * * *")
//    public void launchDailySettlementJob() {
//        LocalDate targetDate = LocalDate.now().minusDays(1);
//        log.info("[SettlementScheduler] 일별 정산대상 수집 배치 시작 - targetDate: {}", targetDate);
//        try {
//            JobParameters params = new JobParametersBuilder()
//                .addLocalDate("targetDate", targetDate)
//                .toJobParameters();
//            jobOperator.start(dailySettlementJob, params);
//        } catch (Exception e) {
//            log.error("[SettlementScheduler] 일별 정산대상 수집 배치 실패", e);
//        }
//    }
//
//    // 매월 1일 10:10 실행
//    @Scheduled(cron = "0 10 10 1 * *")
//    public void launchMonthlySettlementJob() {
//        String yearMonth = YearMonth.now().minusMonths(1).toString();
//        log.info("[SettlementScheduler] 월 정산 배치 시작 - yearMonth: {}", yearMonth);
//        try {
//            JobParameters params = new JobParametersBuilder()
//                .addString("yearMonth", yearMonth)
//                .toJobParameters();
//            jobOperator.start(monthlySettlementJob, params);
//        } catch (Exception e) {
//            log.error("[SettlementScheduler] 월 정산 배치 실패", e);
//        }
//    }
//}
