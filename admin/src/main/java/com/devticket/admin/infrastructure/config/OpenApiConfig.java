package com.devticket.admin.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adminOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("DevTicket Admin API")
                .description("관리자 서비스 API 문서")
                .version("v1.0.0"));
    }

}
