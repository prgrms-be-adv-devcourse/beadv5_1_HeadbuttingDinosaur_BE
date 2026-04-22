package com.devticket.admin.infrastructure.persistence.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.devticket.admin.domain.model.TechStackDocument;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@RequiredArgsConstructor
@Repository
public class TechStackEsRepositoryImpl implements TechStackEsRepository{

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX = "techstack";

    @Override
    public void save(Long id, String name, float[] embedding) {
        try {
            TechStackDocument document = TechStackDocument.builder()
                .id(String.valueOf(id))
                .name(name)
                .embedding(embedding)
                .build();

            IndexRequest<TechStackDocument> request = IndexRequest.of(i -> i
                .index(INDEX)
                .id(String.valueOf(id))
                .document(document)
            );

            elasticsearchClient.index(request);
            log.info("[ES] TechStack 저장 완료 - id: {}, name: {}", id, name);
        } catch (Exception e) {
            log.error("[ES] TechStack 저장 실패 - id: {}", id, e);
            throw new RuntimeException("TechStack ES 저장 실패", e);
        }
    }

    @Override
    public void update(Long id, String name, float[] embedding) {
        save(id, name, embedding);
    }

    @Override
    public void delete(Long id) {
        DeleteRequest request = DeleteRequest.of(d -> d
            .index(INDEX)
            .id(String.valueOf(id)));
        try {
            elasticsearchClient.delete(request);
            log.info("[ES] TechStack 삭제 완료 - id: {}", id);
        } catch (IOException e) {
            log.error("[ES] TechStack 삭제 실패 - id: {}", id, e);
            throw new RuntimeException("TechStack ES 삭제 실패", e);
        }
    }


}
