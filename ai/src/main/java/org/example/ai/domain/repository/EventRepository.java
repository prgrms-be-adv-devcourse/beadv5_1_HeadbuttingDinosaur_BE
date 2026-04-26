package org.example.ai.domain.repository;

import org.example.ai.infrastructure.external.dto.req.PopularEventListRequest;
import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse;

public interface EventRepository {

    PopularEventListResponse getPopularEvents(PopularEventListRequest request);

}
