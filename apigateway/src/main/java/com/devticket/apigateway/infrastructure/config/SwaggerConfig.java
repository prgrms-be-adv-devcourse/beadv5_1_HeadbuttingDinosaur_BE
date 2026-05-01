package com.devticket.apigateway.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local", "test"})
public class SwaggerConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()
            .info(new Info()
                .title("DevTicket Gateway API")
                .description("DevTicket 플랫폼 API Gateway - "
                    + "JWT 인증/인가, 서비스 라우팅을 담당합니다.")
                .version("v1.0"))
            .addSecurityItem(new SecurityRequirement()
                .addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access Token을 입력해주세요. (Bearer 접두사 불필요)")));
    }
}







