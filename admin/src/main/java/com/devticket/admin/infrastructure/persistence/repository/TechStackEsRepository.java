package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.TechStackDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TechStackEsRepository{
    void save(Long id, String name, float[] embedding);
    void update(Long id, String name, float[] embedding);
    void delete(Long id);
}
