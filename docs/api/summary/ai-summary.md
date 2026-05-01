# ai API summary

> 본 문서는 `docs/api/api-overview.md §2 ai` 의 깊이 확장판.
> ⚠ ai 는 ★ 외 트랙 (HIGH 0건). 패키지: `org.example.ai.*` (다른 모듈은 `com.devticket.*`) — 명명 일관성 정정은 후속 트랙.

## 외부 API

**없음** (`@RequestMapping("internal/ai")` 로 internal 만 노출).

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Recommendation Internal | POST | `/internal/ai/recommendation` | `RecommendationController#recommend` | `RecommendationRequest` (`userId` `@NotBlank`) | `RecommendationResponse` (`userId`, `eventIdList`) | event 모듈의 `/api/events/user/recommendations` 위임 추정 (⚠ 정확한 caller 코드 미확인) | `recommendByUserVector` 호출 → `UserVector` 부재 시 `recommendByColdStart` 폴백 |

## Kafka

**없음** (`kafka-design.md §3` 표에 ai 행 없음).

> ⚠ `ai/.../infrastructure/config/KafkaConfig.java` 잔존하나 실제 발행/수신 코드 0건. Spring Batch + Kafka starter 의존성 정리 시 함께 검토 (이번 P5 범위 외).

## 외부 의존성 (REST)

| 호출 대상 | 메서드 | 용도 |
|---|---|---|
| member | `getUserTechStack` (`MemberServiceClient`) | 콜드스타트 — 사용자 기술스택 임베딩 조회 |
| event | `getPopularEvents` (`EventServiceClient`) | 폴백/보강용 인기 이벤트 |
| log | `getRecentActionLog` (`LogServiceClient`) | RecentVectorService 입력 — 최근 행동 로그 |
| Elasticsearch | KNN 검색 (`ElasticsearchClient`) | 정규화 벡터 → 후보 이벤트 |
| `TechStackEmbeddingRepository` | 벡터 저장소 | admin 모듈 흐름이 임베딩 산출, ai 는 조회만 |

## DTO 발췌

- **Presentation**: `RecommendationRequest`, `RecommendationResponse`
- **Domain**: `UserVector` (preference/cart/recent/negative 4종 벡터 + `userId`)
- **External (client req/res)**: member 호출용 `UserTechStackRequest/Response`, event 호출용 `PopularEventListRequest/Response`, log 호출용 `ActionLogRequest/Response`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/ai-summary.md`

## 핵심 컴포넌트

- **추천 산출**: `RecommendationService.recommendByUserVector` / `recommendByColdStart` / `searchKnn`
- **행동 벡터 갱신**: `VectorService` (클릭/환불/카트/네거티브 가중치 누적)
- **최근 활동 벡터 재계산**: `RecentVectorService` + Spring Batch (`RecentVectorJobConfig` + `RecentVectorScheduler`)

## ⚠ 미결 / 후속

- ServiceOverview §3 ai MEDIUM 3건 1줄 요약 미처리 (`recommendByUserVector` / `recommendByColdStart` / `searchKnn`)
- K8s HPA 미설정 (ServiceOverview §5 ⚠1)
- 패키지 네이밍 일관성 (`org.example.ai.*`)
- `KafkaConfig` / Kafka starter 의존성 정리

> 이전 자동 자산(폐기된 `api-summary.md`) 의 `/test/kafka` (`KafkaTestController#send`) entry 는 코드 검증 결과 **존재하지 않는 클래스** — 자동 파서 잡음 (`docs/api/api-overview.md §부록 #5` 정정).
