package org.example.ai.infrastructure.config;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.application.service.RecentVectorService;
import org.example.ai.domain.repository.UserVectorRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RecentVectorJobConfig {

    private final RecentVectorService recentVectorService;
    private final UserVectorRepository userVectorRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;


    @Bean
    public Job recentVectorJob(){
        return new JobBuilder("recentVectorJob", jobRepository)
            .start(recentVectorStep())
            .build();
    }

    @Bean
    public Step recentVectorStep() {
        return new StepBuilder("recentVectorStep", jobRepository)
            .tasklet(recentVectorTasklet(), transactionManager)
            .build();
    }

    // 작업 단위
    @Bean
    public Tasklet recentVectorTasklet() {
        return (contribution, chunkContext) ->{
            log.info("[Batch] recent_vector 재계산 시작");

            // ES user-index에서 전체 userId 조회 후 순회
            userVectorRepository.findAll().forEach(userVector -> {
                try{
                    recentVectorService.recalculateRecentVector(userVector.getUserId());
                }
                catch (Exception e){
                    log.error("[Batch] userId 처리 실패 - userId: {}", userVector.getUserId(), e);
                }
            });
            
            log.info("[Batch] recent_vector 재계산 완료");
            return RepeatStatus.FINISHED;
        };

    }


}
