package com.devticket.event.infrastructure.client.dto;

import java.util.List;

public record EmbeddingResponse(List<EmbeddingData> data) {

    /**
     * 응답의 첫 번째 임베딩 벡터를 float[] 배열로 변환
     * OpenAI API는 data[0].embedding에 1536개의 Double 값을 반환함
     */
    public float[] firstVector() {
        if (data == null || data.isEmpty()) {
            return null;
        }

        List<Double> raw = data.get(0).embedding();
        float[] result = new float[raw.size()];

        for (int i = 0; i < raw.size(); i++) {
            result[i] = raw.get(i).floatValue();
        }

        return result;
    }

    /**
     * OpenAI 응답의 data 배열 내 각 객체 구조
     * { "embedding": [0.023, -0.114, ...] }
     */
    public record EmbeddingData(List<Double> embedding) {}
}
