package com.devticket.settlement.infrastructure.batch.job;

import com.devticket.settlement.infrastructure.batch.dto.SellerSettlementData;
import com.devticket.settlement.infrastructure.batch.dto.SettlementResult;
import com.devticket.settlement.infrastructure.batch.step.MonthlySettlementProcessor;
import com.devticket.settlement.infrastructure.batch.step.MonthlySettlementReader;
import com.devticket.settlement.infrastructure.batch.step.MonthlySettlementWriter;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonthlySettlementJobConfig {

    @Bean
    public Job monthlySettlementJob(JobRepository jobRepository, Step monthlySettlementStep) {
        return new JobBuilder("monthlySettlementJob", jobRepository)
            .start(monthlySettlementStep)
            .build();
    }

    @Bean
    public Step monthlySettlementStep(
        JobRepository jobRepository,
        MonthlySettlementReader monthlySettlementReader,
        MonthlySettlementProcessor monthlySettlementProcessor,
        MonthlySettlementWriter monthlySettlementWriter
    ) {
        return new StepBuilder("monthlySettlementStep", jobRepository)
            .<SellerSettlementData, SettlementResult>chunk(10)
            .reader(monthlySettlementReader)
            .processor(monthlySettlementProcessor)
            .writer(monthlySettlementWriter)
            .build();
    }
}