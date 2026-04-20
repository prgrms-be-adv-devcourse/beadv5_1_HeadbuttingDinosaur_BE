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
        log.info("[TechStackEmbedding] 조회 (Mock) - name: {}", techStackName);

        float[] mockEmbedding = new float[1536];
        mockEmbedding[0] = 0.1f;
        mockEmbedding[1] = 0.2f;

        return Optional.of(mockEmbedding);
    }
}
