package org.example.ai.domain.model;


import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;


@Getter
@Builder
@Document(indexName = "user-index")
public class UserVector {

    @Id
    private String id;

    // ============ 장기 취향 벡터 ============ //
    @Field(type = FieldType.Dense_Vector)
    private float[] preferenceVector;

    @Field(type = FieldType.Float)
    private float preferenceWeightSum;
    // ===================================== //

    // ============ 구매 의도 벡터 ============ //
    @Field(type = FieldType.Dense_Vector)
    private float[] cartVector;

    @Field(type = FieldType.Float)
    private float cartWeightSum;
    // ===================================== //

    // ============ 최근 관심 벡터 ============ //
    @Field(type = FieldType.Dense_Vector)
    private float[] recentVector;

    @Field(type = FieldType.Float)
    private float recentWeightSum;
    // ===================================== //

    // ============ 부정 신호 vector ============ //
    @Field(type = FieldType.Dense_Vector)
    private float[] negativeVector;

    @Field(type = FieldType.Float)
    private float negativeWeightSum;
    // ===================================== //

    // vector 갱신 시각
    @Field(type = FieldType.Date)
    private String updatedAt;



}

