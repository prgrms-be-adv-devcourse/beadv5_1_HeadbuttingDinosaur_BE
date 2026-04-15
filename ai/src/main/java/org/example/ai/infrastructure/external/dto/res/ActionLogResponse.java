package org.example.ai.infrastructure.external.dto.res;

import java.util.List;

public record ActionLogResponse(
    String userId,
    List<ActionLogEntry> logs
) {

    public record ActionLogEntry(
       String eventId,
       String actionType,
       Integer dwellTimeSeconds
    ){}

}
