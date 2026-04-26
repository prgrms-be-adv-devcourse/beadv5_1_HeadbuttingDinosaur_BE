package org.example.ai.infrastructure.persistence.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.domain.repository.TechStackEmbeddingRepository;
import org.springframework.stereotype.Repository;


@Slf4j
@RequiredArgsConstructor
@Repository
public class TechStackEmbeddingRepositoryImpl implements TechStackEmbeddingRepository {

    private final ElasticsearchClient elasticsearchClient;

    // mock 데이터
    @Override
    public Optional<float[]> findEmbeddingByName(String techStackName) {
        log.info("[TechStackEmbedding] 조회 - name: {}", techStackName);

        try {
            var response = elasticsearchClient.search(s -> s
                    .index("techstack")
                    .query(q -> q
                        .term(t -> t
                            .field("name")
                            .value(techStackName)
                        )
                    )
                    .source(src -> src
                        .filter(f -> f
                            .includes("embedding")
                        )
                    ),
                java.util.Map.class
            );

            if (response.hits().hits().isEmpty()) {
                log.warn("[TechStackEmbedding] 조회 결과 없음 - name: {}", techStackName);
                return Optional.empty();
            }

            var source = response.hits().hits().get(0).source();
            if (source == null || !source.containsKey("embedding")) {
                return Optional.empty();
            }

            java.util.List<Double> embeddingRaw = (java.util.List<Double>) source.get("embedding");
            float[] embedding = new float[embeddingRaw.size()];
            for (int i = 0; i < embeddingRaw.size(); i++) {
                embedding[i] = embeddingRaw.get(i).floatValue();
            }

            return Optional.of(embedding);
        } catch (Exception e) {
            log.error("[TechStackEmbedding] 조회 실패 - name: {}", techStackName, e);
            return Optional.empty();
        }
    }
}
