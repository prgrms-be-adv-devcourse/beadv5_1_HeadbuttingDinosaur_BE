package com.devticket.apigateway.infrastructure.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.ConnectException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-1)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        GatewayErrorCode errorCode = resolveErrorCode(ex);

        log.error("Gateway 예외 발생: path={}, errorCode={}, message={}",
            exchange.getRequest().getURI().getPath(),
            errorCode.getCode(),
            ex.getMessage());

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
        } catch (Exception e) {
            log.error("에러 응답 직렬화 실패", e);
            return response.setComplete();
        }
    }

    private GatewayErrorCode resolveErrorCode(Throwable ex) {

        if (ex instanceof ConnectException) {
            return GatewayErrorCode.SERVICE_UNAVAILABLE;
        }

        if (ex.getCause() instanceof ConnectException) {
            return GatewayErrorCode.SERVICE_UNAVAILABLE;
        }

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status != null) {
                return switch (status) {
                    case NOT_FOUND -> GatewayErrorCode.SERVICE_CONNECT_FAILED;
                    case SERVICE_UNAVAILABLE -> GatewayErrorCode.SERVICE_UNAVAILABLE;
                    case UNAUTHORIZED -> GatewayErrorCode.AUTHENTICATION_REQUIRED;
                    case FORBIDDEN -> GatewayErrorCode.ACCESS_DENIED;
                    default -> GatewayErrorCode.INTERNAL_SERVER_ERROR;
                };
            }
        }

        return GatewayErrorCode.INTERNAL_SERVER_ERROR;
    }
}