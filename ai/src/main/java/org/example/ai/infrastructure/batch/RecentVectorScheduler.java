package org.example.ai.infrastructure.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RecentVectorScheduler {

    private final JobOperator jobOperator;
    private final Job recentVectorJob;

    // 매주 월요일 03 시
//    @Scheduled(cron = "0 0  3 * * MON")
    // 테스트용 10초
    @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
    public void runRecentVectorJob(){
        try{
            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            jobOperator.run(recentVectorJob, params);
            log.info("[Scheduler] recent_vector Job 실행 완료");
        }catch (Exception e){
            log.error("[Scheduler] recent_vector Job 실행 실패", e);
        }
    }








}
