package com.devticket.settlement.infrastructure.batch.job;

import com.devticket.settlement.infrastructure.batch.step.DailySettlementTasklet;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DailySettlementJobConfig {

    @Bean
    public Job dailySettlementJob(JobRepository jobRepository, Step dailySettlementStep) {
        return new JobBuilder("dailySettlementJob", jobRepository)
            .start(dailySettlementStep)
            .build();
    }

    @Bean
    public Step dailySettlementStep(
        JobRepository jobRepository,
        DailySettlementTasklet dailySettlementTasklet
    ) {
        return new StepBuilder("dailySettlementStep", jobRepository)
            .tasklet(dailySettlementTasklet)
            .build();
    }
}