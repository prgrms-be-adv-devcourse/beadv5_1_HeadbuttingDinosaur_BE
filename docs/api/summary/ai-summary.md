# ai API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)
> ⚠ 패키지: `org.example.ai.*` (다른 모듈은 `com.devticket.*`) — 명명 일관성 정정은 후속 트랙.

★ 요구사항 :
- 사용자 맞춤 AI 추천 시스템
- AI 중단 시 구매 정상 (격리) — 호출 주체(event 모듈) 측 try-catch 폴백 진입점
- AI 추천 시스템 / 벡터DB 문서 검색

## 외부 API

**없음** (`@RequestMapping("internal/ai")` 로 internal 만 노출).

## 내부 API

| HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|
| POST | `/internal/ai/recommendation` ★ | `RecommendationController#recommend` | event 모듈의 `/api/events/user/recommendations` 위임 | `recommendByUserVector` 호출 → `UserVector` 부재 시 `recommendByColdStart` 폴백 |

## Kafka

**없음** (`kafka-design.md §3` 표에 ai 행 없음).

## 외부 의존성 (REST)

| 호출 대상 | 메서드 | 용도 |
|---|---|---|
| member | `getUserTechStack` (`MemberServiceClient`) ★ | 콜드스타트 — 사용자 기술스택 임베딩 조회 |
| event | `getPopularEvents` (`EventServiceClient`) ★ | 폴백/보강용 인기 이벤트 |
| log | `getRecentActionLog` (`LogServiceClient`) ★ | RecentVectorService 입력 — 최근 행동 로그 |
| Elasticsearch | KNN 검색 (`ElasticsearchClient`) ★ | 정규화 벡터 → 후보 이벤트 |
| `TechStackEmbeddingRepository` ★ | 벡터 저장소 | admin 모듈 흐름이 임베딩 산출, ai 는 조회만 |


## 핵심 컴포넌트

- **추천 산출**: `RecommendationService.recommendByUserVector` / `recommendByColdStart` / `searchKnn`
- **행동 벡터 갱신**: `VectorService` (클릭/환불/카트/네거티브 가중치 누적)
- **최근 활동 벡터 재계산**: `RecentVectorService` + Spring Batch (`RecentVectorJobConfig` + `RecentVectorScheduler`)

