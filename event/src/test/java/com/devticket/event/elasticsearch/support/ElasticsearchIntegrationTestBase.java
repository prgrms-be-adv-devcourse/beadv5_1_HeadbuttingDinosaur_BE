package com.devticket.event.elasticsearch.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 기반 ES 통합 테스트 공통 베이스.
 *
 * 컨테이너는 JVM 당 한 번만 기동되어 모든 하위 테스트 클래스가 재사용한다(싱글턴 패턴).
 * xpack.security.enabled=false로 HTTP 통신을 사용해 SSL 설정 없이 테스트 가능.
 */
@Testcontainers
public abstract class ElasticsearchIntegrationTestBase {

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        // 로컬 실행 중인 ES 사용 (9.2.5)
        registry.add("spring.elasticsearch.uris", () -> "http://localhost:9200");
    }
}
