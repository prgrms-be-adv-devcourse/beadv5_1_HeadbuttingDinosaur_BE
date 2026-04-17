package com.devticket.event.infrastructure.client.dto;

public record EmbeddingRequest(String input, String model) {

    /**
     * 임베딩 텍스트로 요청 객체 생성
     * model은 항상 "text-embedding-3-small" (1536차원)
     */
    public static EmbeddingRequest of(String text) {
        return new EmbeddingRequest(text, "text-embedding-3-small");
    }
}
