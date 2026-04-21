package com.devticket.member.infrastructure.external.dto.req;

public record EmbeddingRequest(
    String model,
    String input
) {

}
