package com.devticket.commerce.order.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    //RestClient는 Spring 6.1부터 도입된 인터페이스로,
    // 실제 구현체는 직접 작성하는 것이 아니라 Spring이 제공하는 Builder를 통해 생성하여 빈(Bean)으로 등록하고 주입받아 사용합니다.

    // 1. Event 서비스용 설정
    @Bean
    public RestClient eventRestClient(@Value("${external.event-base-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
