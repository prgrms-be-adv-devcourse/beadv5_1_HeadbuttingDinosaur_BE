package com.devticket.admin.application.event;

public record TechStackCreatedEvent(
    Long id,
    String name,
    float[] embedding
) {

}
