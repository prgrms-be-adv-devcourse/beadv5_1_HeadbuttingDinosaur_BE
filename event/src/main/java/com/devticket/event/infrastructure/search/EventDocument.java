package com.devticket.event.infrastructure.search;

import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;

@Getter
@NoArgsConstructor
@Document(indexName = "event")
@Mapping(mappingPath = "elasticsearch/event-mapping.json")
public class EventDocument {

    @Id
    private String id;

    /**
     * AI 추천 서비스는 _source.eventId 로 필드를 추출 (PR #540 계약).
     * ES _id 는 hit._source 에 포함되지 않으므로 body 에 명시 저장.
     */
    @Field(type = FieldType.Keyword)
    private String eventId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private List<String> techStacks;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String sellerId;

    /**
     * 1536차원 embedding 벡터.
     * Spring Data ES 컨버터가 dense_vector를 직렬화하지 못하므로
     * syncToElasticsearch()에서 esClient.index()로 직접 저장.
     */
    private List<Float> embedding;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]")
    private LocalDateTime indexedAt;

    @Builder
    private EventDocument(String id, String eventId, String title, String category, List<String> techStacks,
        String status, String sellerId, LocalDateTime indexedAt) {
        this.id = id;
        this.eventId = eventId;
        this.title = title;
        this.category = category;
        this.techStacks = techStacks;
        this.status = status;
        this.sellerId = sellerId;
        this.indexedAt = indexedAt;
    }

    public static EventDocument from(Event event) {
        List<String> techStackNames = event.getEventTechStacks().stream()
            .map(ts -> ts.getTechStackName())
            .toList();

        return EventDocument.builder()
            .id(event.getEventId().toString())
            .eventId(event.getEventId().toString())
            .title(event.getTitle())
            .category(event.getCategory().name())
            .techStacks(techStackNames)
            .status(event.getStatus().name())
            .sellerId(event.getSellerId().toString())
            .indexedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
            .build();
    }

}
