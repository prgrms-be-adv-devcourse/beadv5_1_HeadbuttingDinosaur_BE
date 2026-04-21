package com.devticket.member.infrastructure.external.dto.res;

import java.util.List;

public record EmbeddingResponse(
    List<EmbeddingData> data
) {

    public record EmbeddingData(
        List<Double> embedding
    ){

    }

}
