package com.devticket.admin.infrastructure.persistence.repository;

import com.devticket.admin.domain.model.TechStackDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TechStackEsRepository extends ElasticsearchRepository<TechStackDocument, String> {
//    void save(TechStackDocument techStackDocument);
}
