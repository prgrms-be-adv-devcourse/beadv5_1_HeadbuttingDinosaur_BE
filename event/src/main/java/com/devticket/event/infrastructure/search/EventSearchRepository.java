package com.devticket.event.infrastructure.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSearchRepository extends ElasticsearchRepository<EventDocument, String> {
}
