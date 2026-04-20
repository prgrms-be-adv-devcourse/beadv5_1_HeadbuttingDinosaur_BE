package org.example.ai.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.example.ai.domain.repository.EventRepository;
import org.example.ai.infrastructure.external.client.EventServiceClient;
import org.example.ai.infrastructure.external.dto.req.PopularEventListRequest;
import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepository {

    private final EventServiceClient eventServiceClient;

    @Override
    public PopularEventListResponse getPopularEvents(PopularEventListRequest popularEventRquest) {
        return eventServiceClient.getPopularEvents(popularEventRquest);
    }
}
