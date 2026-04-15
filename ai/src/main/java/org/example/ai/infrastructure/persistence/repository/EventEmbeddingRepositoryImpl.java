package org.example.ai.infrastructure.persistence.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.domain.repository.EventEmbeddingRepository;
import org.springframework.stereotype.Repository;

@Slf4j
@RequiredArgsConstructor
@Repository
public class EventEmbeddingRepositoryImpl implements EventEmbeddingRepository {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public Optional<float[]> findEmbeddingById(String eventId) {
        try {
            // es에서 eventId로 embedding 값 단건 조회
            var response = elasticsearchClient.get(g->g
                .index("event-index")
                .id(eventId)
                    .sourceIncludes("embedding")
                , java.util.Map.class
            );

            log.info("[ES] event 조회 결과 - eventId: {}, found: {}", eventId, response.found());


            // 해당 eventId의 document가 없음 -> Optional
            if(!response.found()){
                return Optional.empty();
            }

            log.info("[ES] source keys: {}", response.source() != null ? response.source().keySet() : "null");

            // document에서 embedding 필드 꺼내기
            List<Double> embedding = (List<Double>) response.source().get("embedding");

            log.info("[ES] embedding null 여부: {}", embedding == null);

            if (embedding == null) {
                return Optional.empty();
            }

            float[] result = new float[embedding.size()];

            for(int i = 0; i < embedding.size(); i++){
                result[i] = embedding.get(i).floatValue();
            }

            return Optional.of(result);

        } catch (Exception e) {
            log.error("[ES] event embedding 조회 실패 - eventId: {}", eventId, e);
            throw new RuntimeException("[ES] event embedding 조회 실패 - eventId: " + eventId, e);
        }


    }





}
