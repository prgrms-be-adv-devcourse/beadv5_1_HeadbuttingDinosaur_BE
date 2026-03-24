package com.devticket.apigateway.infrastructure.security;


import com.devticket.apigateway.infrastructure.exception.ErrorResponse;
import com.devticket.apigateway.infrastructure.exception.GatewayErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public GatewayAuthenticationEntryPoint() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 에러 코드에 해당하는 JSON 에러 응답을 반환합니다.
     */
    public Mono<Void> writeErrorResponse(ServerHttpResponse response, GatewayErrorCode errorCode) {
        response.setStatusCode(HttpStatus.valueOf(errorCode.getStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of(
            errorCode.getStatus(),
            errorCode.getCode(),
            errorCode.getMessage()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
