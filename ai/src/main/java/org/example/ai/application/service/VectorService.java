package org.example.ai.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final UserVectorRepository userVectorRepository;
    private final EventEmbeddingRepository eventEmbeddingRepository;

    // PURCHASE → preference_vector 갱신
    public void updatePreferenceVector(String userId, String eventId){
        updateVector(userId, eventId, 5.0f, "preference");
    }

    // REFUND → preference_vector 역보정 + negative_vector 갱신
    public void updateRefund(String userId, String eventId){
        updateVector(userId, eventId, -5.0f, "preference");
        updateVector(userId, eventId, 3.0f, "negative");
    }

    // CART_ADD → cart_vector 갱신
    public void updateCartVector(String userId, String eventId){
        updateVector(userId, eventId, 3.0f, "cart");
    }

    // CART_REMOVE → negative_vector 갱신
    public void updateNegativeVector(String userId, String eventId){
        updateVector(userId, eventId, 3.0f, "negative");
    }

    // User_Vector 업데이트
    private void updateVector(String userId, String eventId, float weight, String vectorType){

        // 1. event Embedding 조회
        float[] eventEmbedding = eventEmbeddingRepository.findEmbeddingById(eventId)
            .orElse(null);

        if(eventEmbedding == null){
            log.warn("[Vector] event embedding 없음 - eventId: {}", eventId);
            return;
        }

        // 2. 기존 User_Vector 조회, 없으면 -> 신규 생성
        UserVector userVector = userVectorRepository.findById(userId)
            .orElse(UserVector.builder()
                .userId(userId)
                .preferenceVector(new float[1536])
                .preferenceWeightSum(0f)
                .recentVector(new float[1536])
                .recentWeightSum(0f)
                .cartVector(new float[1536])
                .cartWeightSum(0f)
                .negativeVector(new float[1536])
                .negativeWeightSum(0f)
                .updatedAt(Instant.now().toString())
                .build());

        // 3. 벡터 갱신 공식 적용
        float[] updatedVector = computeVector(
            getVector(userVector, vectorType),
            getWeightSum(userVector, vectorType),
            eventEmbedding,
            weight
        );

        // 가중합
        float newWeightSum = getWeightSum(userVector, vectorType) + weight;

        // 갱신된 벡터로 저장
        UserVector updated = buildUpdatedUserVector(userVector, vectorType, updatedVector, newWeightSum);

        userVectorRepository.save(updated);
    }


    // 벡터 갱신 공식 메서드
    private float[] computeVector(float[] current, float currentWeightSum, float[] eventEmbedding, float weight){
        float[] result = new float[current.length];
        float totalWeight = currentWeightSum + weight;

        for(int i = 0; i < current.length; i++){
            result[i] = (current[i] * currentWeightSum + eventEmbedding[i] * weight) / totalWeight;
        }
        return result;
    }

    private float[] getVector(UserVector userVector, String vectorType){
        return switch (vectorType){
            case "preference" -> userVector.getPreferenceVector();
            case "cart"       -> userVector.getCartVector();
            case "negative"   -> userVector.getNegativeVector();
            default -> throw new IllegalArgumentException("알 수 없는 vectorType: " + vectorType);
        };
    }



    private float getWeightSum(UserVector userVector, String vectorType){
        return switch (vectorType){
            case "preference" -> userVector.getPreferenceWeightSum();
            case "cart"       -> userVector.getCartWeightSum();
            case "negative"   -> userVector.getNegativeWeightSum();
            default -> throw new IllegalArgumentException("알 수 없는 vectorType: " + vectorType);
        };
    }

    private UserVector buildUpdatedUserVector(UserVector origin, String vectorType, float[] updatedVector, float newWeightSum){
        return UserVector.builder()
            .userId(origin.getUserId())
            .preferenceVector(vectorType.equals("preference") ? updatedVector : origin.getPreferenceVector())
            .preferenceWeightSum(vectorType.equals("preference") ? newWeightSum : origin.getPreferenceWeightSum())
            .recentVector(origin.getRecentVector())
            .recentWeightSum(origin.getRecentWeightSum())
            .cartVector(vectorType.equals("cart") ? updatedVector : origin.getCartVector())
            .cartWeightSum(vectorType.equals("cart") ? newWeightSum : origin.getCartWeightSum())
            .negativeVector(vectorType.equals("negative") ? updatedVector : origin.getNegativeVector())
            .negativeWeightSum(vectorType.equals("negative") ? newWeightSum : origin.getNegativeWeightSum())
            .updatedAt(Instant.now().toString())
            .build();
    }

}
