package org.example.ai.domain.repository;

import java.util.Optional;

public interface TechStackEmbeddingRepository {

    Optional<float[]> findEmbeddingByName(String techStackName);

}
