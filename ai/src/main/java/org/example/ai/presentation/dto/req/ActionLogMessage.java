package org.example.ai.presentation.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ActionLogMessage(
    @JsonProperty("userId")
    String userId,

    @JsonProperty("eventId")
    String eventId,

    @JsonProperty("eventIds")
    List<String> eventIds,

    @JsonProperty("actionType")
    String actionType,

    @JsonProperty("searchKeyword")
    String searchKeyword,

    @JsonProperty("stackFilter")
    String stackFilter,

    @JsonProperty("dwellTimeSeconds")
    Integer dwellTimeSeconds,

    @JsonProperty("quantity")
    Integer quantity,

    @JsonProperty("totalAmount")
    Long totalAmount,

    @JsonProperty("timestamp")
    String timestamp
) {

}
