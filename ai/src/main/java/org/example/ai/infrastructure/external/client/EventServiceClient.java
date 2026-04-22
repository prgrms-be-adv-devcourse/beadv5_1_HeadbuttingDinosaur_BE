package org.example.ai.infrastructure.external.client;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.infrastructure.external.dto.req.PopularEventListRequest;
import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RequiredArgsConstructor
@Component
public class EventServiceClient {

    private final WebClient webClient;

    @Value("${internal.event-service.url}")
    private String eventServiceUrl;

    public PopularEventListResponse getPopularEvents(PopularEventListRequest request) {
        log.info("[EventClient] 인기 이벤트 조회 - neededCount: {}", request.neededCount());

        try {
            return webClient.post()
                .uri(eventServiceUrl + "/internal/events/popular")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PopularEventListResponse.class)
                .block();
        } catch (Exception e) {
            log.error("[EventClient] 인기 이벤트 조회 실패", e);
            return new PopularEventListResponse(List.of());
        }
    }

}
