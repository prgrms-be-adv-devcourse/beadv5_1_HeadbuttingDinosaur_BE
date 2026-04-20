package org.example.ai.infrastructure.persistence.repository;

import org.example.ai.domain.model.UserVector;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserVectorESRepository extends ElasticsearchRepository<UserVector, String> {

}
