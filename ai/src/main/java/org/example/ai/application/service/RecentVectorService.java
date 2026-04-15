package org.example.ai.application.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;
import org.example.ai.infrastructure.external.client.LogServiceClient;
import org.example.ai.infrastructure.external.dto.res.ActionLogResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecentVectorService {

    private final UserVectorRepository userVectorRepository;
    private final EventEmbeddingRepository eventEmbeddingRepository;
    private final LogServiceClient logServiceClient;


    public void recalculateRecentVector(String userId){
        // 1. Log Service에서 최근 7 일 조회
        ActionLogResponse response = logServiceClient.getRecentActionLog(userId);

        if (response == null || response.logs() == null || response.logs().isEmpty()) {
            log.info("[RecentVector] 로그 없음 - userId: {}", userId);
            return;
        }

        // 2. eventId 별 가중치 합산
        // VIEW/DETAIL_VIEW : eventId 3 회 이상 -> 무시
        Map<String, Integer> detailViewCount = new HashMap<>();
        Map<String, Integer> viewCount = new HashMap<>();
        Map<String, Float> eventWeightMap = new HashMap<>();

        for(ActionLogResponse.ActionLogEntry log : response.logs()){
            String eventId = log.eventId();
            float weight = 0f;

            switch (log.actionType()){
                case "VIEW" -> {
                    int count = viewCount.getOrDefault(eventId,0);
                    if(count >= 3) continue; // view 3회 이상 -> 무시
                    viewCount.put(eventId, count + 1);
                    weight = 0.3f;
                }
                case "DETAIL_VIEW" -> {
                    int count = detailViewCount.getOrDefault(eventId,0);
                    if(count >= 3) continue;
                    detailViewCount.put(eventId, count + 1);
                    weight = 1.0f;
                }
                case "DWELL_TIME" -> {
                    if(log.dwellTimeSeconds() == null || log.dwellTimeSeconds() < 5) continue;
                    weight = dwellTimeWeight(log.dwellTimeSeconds());
                }
                default -> {continue;}
            }

            // eventId가 같다면 -> weight에 더하기
            eventWeightMap.merge(eventId, weight, Float::sum);
        } // for 문 종료

        if(eventWeightMap.isEmpty()){
            log.info("[RecentVector] 유효한 로그 없음 - userId: {}", userId);
            return;
        }

        // 3. 가중 평균으로 recent_vector 계산
        float[] newRecentVector = new float[1536];
        float totalWeight = 0f;

        for(Map.Entry<String, Float> entry : eventWeightMap.entrySet()){
            float[] embedding = eventEmbeddingRepository.findEmbeddingById(entry.getKey())
                .orElse(null);

            if (embedding == null) {
                log.warn("[RecentVector] embedding 없음 - eventId: {}", entry.getKey());
                continue;
            }

            float w = entry.getValue();

            for(int i = 0; i < 1536; i++){
                newRecentVector[i] += embedding[i] * w;
            }

            totalWeight += w;
        }

        if (totalWeight == 0) {
            log.warn("[RecentVector] totalWeight 0 - userId: {}", userId);
            return;
        }

        for (int i = 0; i < 1536; i++) {
            newRecentVector[i] /= totalWeight;
        }

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

        UserVector updated = UserVector.builder()
            .userId(userId)
            .preferenceVector(userVector.getPreferenceVector())
            .preferenceWeightSum(userVector.getPreferenceWeightSum())
            .recentVector(newRecentVector)
            .recentWeightSum(totalWeight)
            .cartVector(userVector.getCartVector())
            .cartWeightSum(userVector.getCartWeightSum())
            .negativeVector(userVector.getNegativeVector())
            .negativeWeightSum(userVector.getNegativeWeightSum())
            .updatedAt(Instant.now().toString())
            .build();

        userVectorRepository.save(updated);
        log.info("[RecentVector] 갱신 완료 - userId: {}, totalWeight: {}", userId, totalWeight);
    }

    
    private float dwellTimeWeight(int seconds){
        if (seconds < 5)  return 0f;
        if (seconds < 30) return 0.5f;
        if (seconds < 60) return 1.0f;
        return 2.0f;
    }
    
    
    
    
    
    
    
    
}
