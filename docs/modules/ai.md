# ai

> 본 페이지는 ServiceOverview.md §3 ai 섹션의 확장판입니다.
> ⚠ ai 모듈은 ★ 핵심 플로우 외 트랙이며 `service-status.md` 미등재 (HIGH 0건). 본 페이지의 1줄 요약은 ServiceOverview §3 1줄 초안 기준이며, MEDIUM 3건(`recommendByUserVector` / `recommendByColdStart` / `searchKnn`)은 미처리 상태.
> ⚠ 패키지 네이밍: 본 모듈만 `org.example.ai.*` (다른 모듈은 `com.devticket.*`). 명명 일관성 정정은 후속 트랙.

## 1. 모듈 책임

벡터 기반 이벤트 추천. 사용자 기술스택 임베딩(콜드스타트) + 행동 벡터(선호/카트/체류/환불) 합성으로 선호 벡터를 도출하고, Elasticsearch KNN 으로 후보 이벤트를 조회한 뒤 재정렬해 추천 목록을 반환한다.

**구성 요소**:
- 추천 산출(`RecommendationService`): `recommendByUserVector` (정상 경로) / `recommendByColdStart` (`UserVector` 없을 때 기술스택 임베딩 사용) / `searchKnn` (ES KNN 호출).
- 행동 벡터 갱신(`VectorService`): 클릭/환불/카트/네거티브 이벤트별 가중치 누적.
- 최근 활동 벡터 재계산(`RecentVectorService` + Spring Batch `RecentVectorJobConfig` + `RecentVectorScheduler`): action.log 기반 최근 행동 벡터를 주기적으로 재계산.
- 인프라 설정: `ElasticsearchConfig` / `ElasticsearchIndexInitializer` (KNN 인덱스 초기화), `WebClientConfig` (외부 호출), `KafkaConfig`(⚠ 실제 발행/수신 0건 — Spring Batch Kafka starter 잔존 가능), `BatchTransactionConfig`.

**위임 (담당 안 함)**:
- 회원 / 기술스택 데이터 → member 모듈 (REST `getUserTechStack`)
- 이벤트 / 인기도 데이터 → event 모듈 (REST `getPopularEvents`)
- 행동 로그 → log 모듈 (REST `getRecentActionLog`)
- OpenAI 임베딩 호출 자체는 admin 모듈의 `TechStackEmbedding` 흐름이 작성하며, ai 측은 `TechStackEmbeddingRepository`로 조회만 수행

## 2. 외부 API

**없음**. `RecommendationController` 가 있는 유일한 컨트롤러이며, 매핑은 `@RequestMapping("internal/ai")` 로 내부용.

## 3. 내부 API (다른 서비스가 호출)

| 영역 | 메서드 | 경로 | Controller 메서드 | 요청 DTO | 응답 DTO | 설명 |
|---|---|---|---|---|---|---|
| Recommendation Internal | POST | `/internal/ai/recommendation` | `RecommendationController#recommend` | `RecommendationRequest` (`userId`) | `RecommendationResponse` (`userId`, `eventIdList`) | 사용자 추천 이벤트 ID 목록 반환. 내부적으로 `recommendByUserVector` 호출, `UserVector` 없으면 `recommendByColdStart` 로 폴백 |

> ⚠ 호출 주체: gateway 또는 event 모듈의 `/api/events/user/recommendations` (event.md §2)에서 위임 추정. 정확한 호출 경로는 ServiceOverview §3 ai "MEDIUM 미처리"로 인계 — 코드 미확인.

## 4. Kafka

### 발행 (Producer)

**없음** (`kafka-design.md §3` line 70-73 표에 ai 행 없음).

### 수신 (Consumer)

**없음**.

> ⚠ `KafkaConfig.java` 파일이 `infrastructure/config` 하위에 잔존하나 실제 발행/수신 코드 0건. Spring Batch / Kafka 의존성 정리 시 함께 검토 권장 (이번 P5 범위 외).

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) 참조. ⚠ 자동 자산이 ai 모듈을 미커버(`docs/standards/docs-parser-standard.md` 모듈 커버리지 누락 참조).

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
  - 외부: Elasticsearch (KNN 검색 — `ElasticsearchClient`), `TechStackEmbeddingRepository` (벡터 저장소 — admin 모듈 흐름이 임베딩 산출)
- **Kafka 구독**: 없음

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**: `POST /internal/ai/recommendation` — 호출 주체는 event 모듈 추천 위임 또는 gateway 라우팅 (⚠ 정확한 caller는 코드 미확인, ServiceOverview §3 ai MEDIUM 인계)
- **Kafka 피구독**: 없음

### ⚠ 미결 (모듈 누적 — ServiceOverview §3 인계)

- **MEDIUM 3건 1줄 요약 미처리** (★ 외 트랙, 발표 후 회고 단계):
  - `recommendByUserVector` — 사용자 선호/카트/최근 행동 벡터를 가중 합성 후 정규화 → KNN 검색 → 재정렬 (코드 분석 필요)
  - `recommendByColdStart` — `UserVector` 부재 시 기술스택 임베딩 합성으로 임시 선호 벡터 산출
  - `searchKnn` — 정규화 벡터 입력으로 ES KNN 호출 (`searchNum` 파라미터)
- **K8s HPA 미설정** (ServiceOverview §5 ⚠1): HPA 매니페스트 부재, `kubectl scale --replicas=1` 수동 운영 — 발표 후 추천 부하 증가 대비 추가 권장
- **패키지 네이밍 불일치**: `org.example.ai.*` (다른 모듈 `com.devticket.*`) — 별도 트랙

### 신규 인프라/구조 변경 (참고)

- **Spring Batch + Kafka starter 의존**: `RecentVectorJobConfig` 가 Spring Batch Job 구성, `RecentVectorScheduler` 가 트리거. 실제 Kafka 발행/수신은 0건이지만 starter 의존이 잔존 (의존성 정리 후속 검토).
- **Elasticsearch KNN 인덱스**: `ElasticsearchIndexInitializer` 가 부팅 시 인덱스 초기화. 운영 환경 ES 장애 시 추천 경로 회복 정책은 미정 (이벤트 검색 ES 폴백과 별개).

처리 계획 상세: [ServiceOverview.md §3 ai](../ServiceOverview.md), §5 ⚠1 (HPA) 참조.
