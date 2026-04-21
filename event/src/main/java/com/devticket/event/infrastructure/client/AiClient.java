package com.devticket.event.infrastructure.client;

import com.devticket.event.infrastructure.client.dto.AiRecommendationRequest;
import com.devticket.event.infrastructure.client.dto.AiRecommendationResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;

    @Value("${service.ai.url}")
    private String aiServiceUrl;

    private static final String RECOMMENDATION_PATH = "/internal/ai/recommendation";

    public List<String> getRecommendedEventIds(UUID userId) {
        try {
            AiRecommendationRequest request = new AiRecommendationRequest(userId.toString());
            AiRecommendationResponse response = restTemplate.postForObject(
                aiServiceUrl + RECOMMENDATION_PATH,
                request,
                AiRecommendationResponse.class
            );
            if (response == null || response.eventIdList() == null) {
                log.warn("[AI 추천 응답 없음] userId: {}", userId);
                return List.of();
            }
            return response.eventIdList();
        } catch (Exception e) {
            log.warn("[AI 추천 호출 실패] userId: {}", userId, e);
            return List.of();
        }
    }
}
