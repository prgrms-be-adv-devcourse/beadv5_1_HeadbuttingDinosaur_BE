package com.devticket.event.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private final ElasticsearchClient esClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;
    private final ObjectMapper objectMapper;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sync(Event event) {
        try {
            List<String> techStackNames = event.getEventTechStacks().stream()
                .map(EventTechStack::getTechStackName)
                .toList();

            String embeddingText = techStackNames.isEmpty()
                ? event.getTitle() + ". Category: " + event.getCategory().name()
                : event.getTitle() + ". Category: " + event.getCategory().name()
                    + ". Tech Stacks: " + String.join(", ", techStackNames);

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", event.getEventId().toString());
            // AI 추천 서비스는 _source.eventId 로 추출 (PR #540 계약). _id 는 hit._source 에 포함되지 않으므로 body 에 명시.
            doc.put("eventId", event.getEventId().toString());
            doc.put("title", event.getTitle());
            doc.put("category", event.getCategory().name());
            doc.put("techStacks", techStackNames);
            doc.put("status", event.getStatus().name());
            doc.put("sellerId", event.getSellerId().toString());
            doc.put("saleStartAt", event.getSaleStartAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            // mapping의 date_hour_minute_second 형식에 맞춤 (나노초 제외)
            doc.put("indexedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

            float[] vector = openAiEmbeddingClient.embed(embeddingText);
            if (vector != null) {
                List<Float> vectorList = new ArrayList<>(vector.length);
                for (float v : vector) vectorList.add(v);
                doc.put("embedding", vectorList);
            } else {
                log.warn("[ES 동기화] embedding 생성 실패 - eventId: {}", event.getEventId());
            }

            String json = objectMapper.writeValueAsString(doc);

            esClient.index(i -> i
                .index("event")
                .id(event.getEventId().toString())
                .withJson(new StringReader(json)));

            log.debug("[ES 동기화 완료] eventId: {}", event.getEventId());
        } catch (Exception e) {
            throw new RuntimeException("[ES 동기화 실패] eventId: " + event.getEventId(), e);
        }
    }

    @Recover
    public void recoverSync(RuntimeException e, Event event) {
        log.error("[ES 동기화 최종 실패] eventId: {} - 수동 확인 필요", event.getEventId(), e);
    }
}
