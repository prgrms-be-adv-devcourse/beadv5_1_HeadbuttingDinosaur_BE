package com.devticket.member.presentation.domain.repository;

import com.devticket.member.presentation.domain.model.TechStack;
import com.devticket.member.presentation.domain.model.TechStackDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TechStackEsRepository extends ElasticsearchRepository<TechStackDocument, String> {
//    void save(TechStackDocument techStackDocument);
}
