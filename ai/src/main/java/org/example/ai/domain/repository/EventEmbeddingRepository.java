package org.example.ai.domain.repository;

import java.util.Optional;

public interface EventEmbeddingRepository {

    Optional<float[]> findEmbeddingById(String eventId);

}
