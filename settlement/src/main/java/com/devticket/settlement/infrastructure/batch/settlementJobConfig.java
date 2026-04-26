package com.devticket.settlement.infrastructure.batch;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class settlementJobConfig {

    private final SettlementItemReader settlementItemReader;
    private final SettlementItemProcessor settlementItemProcessor;
    private final SettlementItemWriter settlementItemWriter;

    // JOB : 전체 배치 작업 정의
    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
            .start(settlementStep)
            .build();
    }

    // Step : Job 안의 단계 정의(Reader -> Processor -> Writer)
    @Bean
    public Step settlementStep(JobRepository jobRepository) {
        return new StepBuilder("settlementStep", jobRepository)
            .<InternalSettlementDataResponse, List<Settlement>>chunk(10)
            .reader(settlementItemReader)
            .processor(settlementItemProcessor)
            .writer(settlementItemWriter)
            .build();
    }

}
