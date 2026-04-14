package org.example.ai.domain.model;


import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;


@Getter
@Builder
@Document(indexName = "user-index", createIndex = false)
public class UserVector {

    @Id
    private String id;

    // ============ 장기 취향 벡터 ============ //
    private float[] preferenceVector;

    private float preferenceWeightSum;
    // ===================================== //

    // ============ 구매 의도 벡터 ============ //
    private float[] cartVector;

    private float cartWeightSum;
    // ===================================== //

    // ============ 최근 관심 벡터 ============ //
    private float[] recentVector;

    private float recentWeightSum;
    // ===================================== //

    // ============ 부정 신호 vector ============ //
    private float[] negativeVector;

    private float negativeWeightSum;
    // ===================================== //

    // vector 갱신 시각
    private String updatedAt;



}

