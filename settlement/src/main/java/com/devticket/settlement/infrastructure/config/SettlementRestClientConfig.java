package com.devticket.settlement.infrastructure.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SettlementRestClientConfig {

    @Bean
    public RestClient settlementToCommerceRestClient(
        @Value("${external.commerce-base-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}