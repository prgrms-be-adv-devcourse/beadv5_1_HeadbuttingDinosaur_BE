package com.devticket.event.infrastructure.search;

import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "event")
public class EventDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String location;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String sellerId;

    @Field(type = FieldType.Integer)
    private Integer price;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime eventDateTime;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime saleStartAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime saleEndAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;

    @Field(type = FieldType.Nested)
    private List<TechStackDocument> techStacks;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TechStackDocument {

        @Field(type = FieldType.Long)
        private Long techStackId;

        @Field(type = FieldType.Keyword)
        private String techStackName;
    }

    public static EventDocument from(Event event) {
        String thumbnail = event.getEventImages().stream()
            .min(Comparator.comparingInt(EventImage::getSortOrder))
            .map(EventImage::getImageUrl)
            .orElse(null);

        List<TechStackDocument> techStacks = event.getEventTechStacks().stream()
            .map(ts -> TechStackDocument.builder()
                .techStackId(ts.getTechStackId())
                .techStackName(ts.getTechStackName())
                .build())
            .toList();

        return EventDocument.builder()
            .id(event.getEventId().toString())
            .title(event.getTitle())
            .description(event.getDescription())
            .location(event.getLocation())
            .category(event.getCategory().name())
            .status(event.getStatus().name())
            .sellerId(event.getSellerId().toString())
            .price(event.getPrice())
            .eventDateTime(event.getEventDateTime())
            .saleStartAt(event.getSaleStartAt())
            .saleEndAt(event.getSaleEndAt())
            .createdAt(event.getCreatedAt())
            .thumbnailUrl(thumbnail)
            .techStacks(techStacks)
            .build();
    }
}
