package com.devticket.event.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("DevTicket Event API")
                .description("이벤트 서비스 API 문서")
                .version("v1.0.0"));
    }
}
