package com.devticket.apigateway.infrastructure.config;

import com.devticket.apigateway.infrastructure.oauth.OAuthFailureHandler;
import com.devticket.apigateway.infrastructure.oauth.OAuthSuccessHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final OAuthSuccessHandler oAuthSuccessHandler;
    private final OAuthFailureHandler oAuthFailureHandler;
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(
        OAuthSuccessHandler oAuthSuccessHandler,
        OAuthFailureHandler oAuthFailureHandler,
        ReactiveClientRegistrationRepository clientRegistrationRepository
    ) {
        this.oAuthSuccessHandler = oAuthSuccessHandler;
        this.oAuthFailureHandler = oAuthFailureHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationRequestResolver(authorizationRequestResolver())
                .authenticationSuccessHandler(oAuthSuccessHandler)
                .authenticationFailureHandler(oAuthFailureHandler)
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .build();
    }

    private ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        DefaultServerOAuth2AuthorizationRequestResolver resolver =
            new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
        resolver.setAuthorizationRequestCustomizer(
            customizer -> customizer.additionalParameters(params -> params.put("prompt", "select_account"))
        );
        return resolver;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:13000","http://127.0.0.1:13000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}