package org.example.ai.infrastructure.external.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.infrastructure.external.dto.req.ActionLogRequest;
import org.example.ai.infrastructure.external.dto.res.ActionLogResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogServiceClient {

    private final WebClient webClient;

    @Value("${internal.log-service.url}")
    private String logServiceUrl;

    public ActionLogResponse getRecentActionLog(String userId) {
        ActionLogRequest request = new ActionLogRequest(userId, "VIEW,DETAIL_VIEW,DWELL_TIME");

        try {
            return webClient.get()
                .uri(logServiceUrl + "/internal/logs/actions",
                    uriBuilder -> uriBuilder
                        .queryParam("userId", request.userId())
                        .queryParam("days", 7)
                        .queryParam("actionTypes", request.actionTypes())
                        .build())
                .retrieve()
                .bodyToMono(ActionLogResponse.class)
                .block();
        } catch (Exception e) {
            log.error("[LogService] 로그 조회 실패 - userId: {}", userId, e);
            return null;
        }
    }



}
