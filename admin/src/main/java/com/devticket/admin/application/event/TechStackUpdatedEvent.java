package com.devticket.admin.application.event;

public record TechStackUpdatedEvent(
    Long id,
    String name,
    float[] embedding
) {

}
