package org.example.ai.application.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.common.exception.BusinessException;
import org.example.ai.domain.exception.AiErrorCode;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.UserVectorRepository;
import org.example.ai.presentation.dto.req.RecommendationRequest;
import org.example.ai.presentation.dto.res.RecommendationResponse;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecommendationService {

    private final UserVectorRepository userVectorRepository;
    private final ElasticsearchClient elasticsearchClient;





    // ================ 실질적인 Service단 유일 메서드(추천 기능) ================= //
    public RecommendationResponse recommendByUserVector(RecommendationRequest request){

        // 1. 추천 요청한 UserId 기준으로 UserVector 조회
        String userId = request.userId();
        UserVector userVector = userVectorRepository.findById(userId)
            .orElse(null);

        if(userVector == null){
            log.warn("[Recommendation] UserVector 없음 - userId: {}", userId);
            return new RecommendationResponse(userId, List.of());
        }

        // 2. 가중합 벡터(방향 + 크기) 연산
        float[] combinedVector = combineVectorByWeight(
            getOrEmpty(userVector.getPreferenceVector()),
            getOrEmpty(userVector.getCartVector()),
            getOrEmpty(userVector.getRecentVector())
        );

        // 3. 정규화 벡터(정규화 -> 크기 제거)
         float[] normalizedVector = normalize(combinedVector);

         // 4. 정규화 벡터 -> kNN 검색 -> 이벤트 후보군 30 개 추출
        List<Map<String, Object>> candidates = searchKnn(normalizedVector);

        // 5. 모든 이벤트 후보군 -> 유저 벡터(4 가지)와 cosine 연산 -> 랭킹 정렬
        List<ScoredEvent> listedScore = reRank(candidates, userVector);

        // 6. -> 5 개 추출
        List<String> topEventIds = listedScore.stream()
            .sorted(Comparator.comparingDouble(ScoredEvent::score).reversed())
            .limit(5)
            .map(ScoredEvent::eventId)
            .toList();

        return new RecommendationResponse(userId, topEventIds);
        }






    // =============== 하위 함수 모음(가중합 * 유저 벡터 -> 가중합 벡터) =============== //
    // 1. 가중치 기반 벡터합
    private float[] combineVectorByWeight(float[] preferenceVector, float[] cartVector, float[] recentVector){
        float[] combinedVector = new float[preferenceVector.length];

        for(int i = 0; i < preferenceVector.length; i++){
            combinedVector[i] =
                preferenceVector[i] * 0.5f + cartVector[i] * 0.3f + recentVector[i] * 0.2f;
        }
        return combinedVector;
    }

    // 2. vector -> 정규화
    private float[] normalize(float[] combinedVector){

        float sum = 0f;
        float[] normalizedVector = new float[combinedVector.length];

        // 크기 계산
        for(int i = 0; i < combinedVector.length; i++){
            sum += combinedVector[i] * combinedVector[i];
        }

        float norm = (float)Math.sqrt(sum);

        // 0 나누기 방지
        if(norm == 0f) return combinedVector;

        // 각 요소를 크기로 나누기
        for(int i = 0; i < combinedVector.length; i++){
            normalizedVector[i] = combinedVector[i] / norm;
        }

        return normalizedVector;
    }

    // 3. normalize된 벡터 -> kNN 검색
    public List<Map<String, Object>> searchKnn(float[] normalizedVector){

        try{
            List<Float> queryList = new ArrayList<>();
            // 배열 -> List
            for(float v :  normalizedVector){
                queryList.add(v);
            }

            // kNN 쿼리문
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index("event-index")
                    .knn(k -> k
                        .field("embedding")
                        .queryVector(queryList)
                        .numCandidates(100)
                        .k(30)
                        .filter(f -> f
                            .term(t -> t
                                .field("status")
                                .value("ON_SALE"))))
                    .source(src -> src
                        .filter(f -> f
                            .includes("eventId", "embedding")
                        )
                    ),
                Map.class);

            List<Map<String, Object>> result = new ArrayList<>();
            for(Hit<Map> hit : response.hits().hits()){
                if (hit.source() != null) {
                    result.add(hit.source());
                }
            }
            return result;
        }
        catch(Exception e){
            log.error("[Recommendation] kNN 검색 실패", e);
            throw new BusinessException(AiErrorCode.EVENT_INDEX_SEARCH_FAILED);
        }
    }
    // ================================================================ //
    // 4-1. ============== kNN 30 개 재정렬 및 cosine 유사도 연산 ============== //
    private List<ScoredEvent> reRank(
        List<Map<String, Object>> candidates,
        UserVector userVector
        ){
        List<ScoredEvent> scoreList = new ArrayList<>();

        for(Map<String, Object> candidate : candidates){
            String eventId = candidate.get("eventId").toString();

            List<Double> embeddingRaw = (List<Double>) candidate.get("embedding");
            if (embeddingRaw == null) continue;

            // ES가 Embedding을 double로 저장 -> 다시 float화 하기
            float[] eventEmbedding = new float[embeddingRaw.size()];
            for (int j = 0; j < embeddingRaw.size(); j++) {
                eventEmbedding[j] = embeddingRaw.get(j).floatValue();
            }

            double score = sim(getOrEmpty(userVector.getPreferenceVector()), eventEmbedding) * 0.45
                + sim(getOrEmpty(userVector.getCartVector()), eventEmbedding) * 0.25
                + sim(getOrEmpty(userVector.getRecentVector()), eventEmbedding) * 0.25
                - sim(getOrEmpty(userVector.getNegativeVector()), eventEmbedding) * 0.15;

            scoreList.add(new ScoredEvent(eventId, score));

        }
        return scoreList;
    }

    private float[] getOrEmpty(float[] vector) {
        return vector != null ? vector : new float[1536];
    }

    // 4-2. consine 유사도 연산 메서드
    private double sim(float[] tendencyVector, float[] eventVector){

        // 내적
        double dot = 0;
        // tendencyVector 벡터 크기
        double tvMag = 0;
        //eventVector 벡터 크기
        double evMag = 0;


        // 내적, 경향성 및 이벤트 벡터 크기 연산
        for(int i = 0; i < tendencyVector.length; i++){
            dot += tendencyVector[i] * eventVector[i];
            tvMag += tendencyVector[i] * tendencyVector[i];
            evMag += eventVector[i] * eventVector[i];
        }

        if(tvMag == 0 || evMag == 0){
            return 0;
        }

        //내적에서 크기 제거
        return dot / (Math.sqrt(tvMag) * Math.sqrt(evMag));

    }

    // 4-3. 스코어 계산 후 -> record 화
    record ScoredEvent(String eventId, double score) {

    }
    // ================================================================ //
    // ================================================================ //



}
