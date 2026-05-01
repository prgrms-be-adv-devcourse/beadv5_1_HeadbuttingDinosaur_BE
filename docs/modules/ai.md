# ai

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)
> ⚠ 패키지 네이밍: 본 모듈만 `org.example.ai.*` (다른 모듈은 `com.devticket.*`).

## 1. 모듈 책임 ★

벡터 기반 이벤트 추천. 사용자 기술스택 임베딩(콜드스타트) + 행동 벡터(선호/카트/체류/환불) 합성으로 선호 벡터를 도출하고, Elasticsearch KNN 으로 후보 이벤트를 조회한 뒤 재정렬해 추천 목록을 반환한다.

★ 요구사항 :
- 사용자 맞춤 AI 추천 시스템
- AI 중단 시 구매 정상 (격리) — `AiClient` try-catch 폴백 진입점이 호출 주체(event 모듈) 측
- AI 추천 시스템 / 벡터DB 문서 검색

**구성 요소**:
- 추천 산출(`RecommendationService`): `recommendByUserVector` (정상 경로) / `recommendByColdStart` (`UserVector` 없을 때 기술스택 임베딩 사용) / `searchKnn` (ES KNN 호출)
- 행동 벡터 갱신(`VectorService`): 클릭/환불/카트/네거티브 이벤트별 가중치 누적
- 최근 활동 벡터 재계산(`RecentVectorService` + Spring Batch `RecentVectorJobConfig` + `RecentVectorScheduler`): action.log 기반 최근 행동 벡터를 주기적으로 재계산
- 인프라 설정: `ElasticsearchConfig` / `ElasticsearchIndexInitializer` (KNN 인덱스 초기화), `WebClientConfig` (외부 호출), `BatchTransactionConfig`

**위임 (담당 안 함)**:
- 회원 / 기술스택 데이터 → member 모듈 (REST `getUserTechStack`)
- 이벤트 / 인기도 데이터 → event 모듈 (REST `getPopularEvents`)
- 행동 로그 → log 모듈 (REST `getRecentActionLog`)
- OpenAI 임베딩 호출 자체는 admin 모듈의 `TechStackEmbedding` 흐름이 작성하며, ai 측은 `TechStackEmbeddingRepository` 로 조회만 수행

## 2. 외부 API

**없음**. `RecommendationController` 가 있는 유일한 컨트롤러이며, 매핑은 `@RequestMapping("internal/ai")` 로 내부용.

## 3. 내부 API (다른 서비스가 호출)

| 영역 | 메서드 | 경로 | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Recommendation Internal | POST | `/internal/ai/recommendation` ★ | `RecommendationController#recommend` | event 모듈 (`/api/events/user/recommendations` 위임 추정) | 사용자 추천 이벤트 ID 목록 반환. 내부적으로 `recommendByUserVector` 호출, `UserVector` 없으면 `recommendByColdStart` 로 폴백 |

## 4. Kafka

### 발행 (Producer)

**없음** (`kafka-design.md §3` 표에 ai 행 없음).

### 수신 (Consumer)

**없음**.

## 5. DTO

상세는 [dto/summary/ai-summary.md](../dto/summary/ai-summary.md) 참조.

- **Presentation**: `RecommendationRequest` (필드: `userId` `@NotBlank`), `RecommendationResponse` (필드: `userId`, `eventIdList`)
- **Domain**: `UserVector` (사용자 선호/카트/최근/네거티브 벡터 4종 + `userId`)
- **External (client req/res — ai 가 외부 호출 시 사용)**:
  - member 호출: `UserTechStackRequest`, `UserTechStackResponse`
  - event 호출: `PopularEventListRequest`, `PopularEventListResponse`
  - log 호출: `ActionLogRequest`, `ActionLogResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - member: `getUserTechStack` (`MemberServiceClient`) — 콜드스타트용 기술스택 임베딩 조회
  - event: `getPopularEvents` (`EventServiceClient`) — 폴백 / 보강용 인기 이벤트
  - log: `getRecentActionLog` (`LogServiceClient`) — 최근 행동 로그 (RecentVectorService 입력)
  - 외부: Elasticsearch (KNN 검색 — `ElasticsearchClient`) ★, `TechStackEmbeddingRepository` (벡터 저장소 — admin 모듈 흐름이 임베딩 산출)
- **Kafka 구독**: 없음

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**: `POST /internal/ai/recommendation` ★ — event 모듈 추천 위임 또는 gateway 라우팅
- **Kafka 피구독**: 없음

### 신규 인프라 / 구조 (참고)

- **Spring Batch**: `RecentVectorJobConfig` 가 Job 구성, `RecentVectorScheduler` 가 트리거 (action.log 기반 최근 벡터 재계산)
- **Elasticsearch KNN 인덱스** ★: `ElasticsearchIndexInitializer` 가 부팅 시 인덱스 초기화 (1536차원 dense_vector, similarity=cosine, k=30, status=ON_SALE 필터)

