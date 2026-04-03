package com.devticket.settlement.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI memberOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("DevTicket Settlement API")
                .description("판매자/정산 서비스 API 문서")
                .version("v1.0.0"));
    }
    
}
