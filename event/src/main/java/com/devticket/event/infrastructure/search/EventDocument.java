package com.devticket.event.infrastructure.search;

import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Setter
@NoArgsConstructor
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
    @Setter
    @NoArgsConstructor
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
            .map(ts -> {
                TechStackDocument doc = new TechStackDocument();
                doc.setTechStackId(ts.getTechStackId());
                doc.setTechStackName(ts.getTechStackName());
                return doc;
            })
            .toList();

        EventDocument doc = new EventDocument();
        doc.setId(event.getEventId().toString());
        doc.setTitle(event.getTitle());
        doc.setDescription(event.getDescription());
        doc.setLocation(event.getLocation());
        doc.setCategory(event.getCategory().name());
        doc.setStatus(event.getStatus().name());
        doc.setSellerId(event.getSellerId().toString());
        doc.setPrice(event.getPrice());
        doc.setEventDateTime(event.getEventDateTime());
        doc.setSaleStartAt(event.getSaleStartAt());
        doc.setSaleEndAt(event.getSaleEndAt());
        doc.setCreatedAt(event.getCreatedAt());
        doc.setThumbnailUrl(thumbnail);
        doc.setTechStacks(techStacks);
        return doc;
    }
}
