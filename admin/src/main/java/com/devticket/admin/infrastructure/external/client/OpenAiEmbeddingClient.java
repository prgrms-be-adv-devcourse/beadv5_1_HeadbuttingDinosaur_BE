package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.req.EmbeddingRequest;
import com.devticket.admin.infrastructure.external.dto.res.EmbeddingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient {

    private final WebClient webClient;

    @Value("${openai.embedding.api-key}")
    private String apiKey;

    @Value("${openai.embedding.url}")
    private String embeddingUrl;

    @Value("${openai.embedding.model}")
    private String model;

    public float[] embed(String text){
        try{

            // 1. openAI embedding 요청
            EmbeddingResponse response = webClient.post()
                .uri(embeddingUrl)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(new EmbeddingRequest(text, model))
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block();

            // 2. Double vector로 반환
            List<Double> vector = response.data().get(0).embedding();

            // 3. Doble -> float으로 변환
            float[] result = new float[vector.size()];
            for(int i = 0; i < vector.size(); i++){
                result[i] = vector.get(i).floatValue();
            }
            return result;

        }catch (Exception e){
            log.error("[OpenAI] 임베딩 생성 실패 - text: {}", text, e);
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }
}
