package com.devticket.event.infrastructure.client;

import com.devticket.event.common.config.OpenAiProperties;
import com.devticket.event.infrastructure.client.dto.EmbeddingRequest;
import com.devticket.event.infrastructure.client.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient {

    private final RestTemplate restTemplate;
    private final OpenAiProperties openAiProperties;

    private static final String OPENAI_EMBEDDING_API = "https://api.openai.com/v1/embeddings";

    /**
     * 텍스트를 OpenAI Embedding API로 처리
     * @param text 임베딩할 텍스트
     * @return 1536차원 float[] 벡터, 실패 시 null
     */
    public float[] embed(String text) {
        // Feature toggle 확인
        if (!openAiProperties.isEnabled()) {
            log.warn("[Embedding 비활성화] enabled=false");
            return null;
        }

        try {
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiProperties.getApiKey());

            // 요청 바디 생성
            EmbeddingRequest request = EmbeddingRequest.of(text);
            HttpEntity<EmbeddingRequest> httpEntity = new HttpEntity<>(request, headers);

            // OpenAI API 호출
            EmbeddingResponse response = restTemplate.postForObject(
                OPENAI_EMBEDDING_API,
                httpEntity,
                EmbeddingResponse.class
            );

            if (response == null) {
                log.warn("[OpenAI Embedding 실패] response null");
                return null;
            }

            float[] vector = response.firstVector();
            if (vector == null) {
                log.warn("[OpenAI Embedding 실패] vector null");
                return null;
            }

            log.debug("[OpenAI Embedding 성공] text 길이: {}, 벡터 차원: {}", text.length(), vector.length);
            return vector;

        } catch (Exception e) {
            log.warn("[OpenAI Embedding 실패] text 길이: {}", text.length(), e);
            return null;  // Graceful degradation: embedding 실패해도 이벤트 등록은 계속
        }
    }
}
