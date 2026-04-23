package com.devticket.event.elasticsearch.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ES 통합 테스트 공통 베이스.
 *
 * <p>로컬 개발 환경에서 기동 중인 ES(`localhost:9200`) 에 직접 연결.
 * CI 환경에서는 {@code @Tag("elasticsearch")} + build.gradle 의 {@code excludeTags "elasticsearch"}
 * 조건으로 본 계열 테스트를 스킵하여 ES 없이도 빌드 통과.
 */
@Testcontainers
public abstract class ElasticsearchIntegrationTestBase {

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        // 로컬 실행 중인 ES 사용 (9.2.5)
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:9200");
    }
}
