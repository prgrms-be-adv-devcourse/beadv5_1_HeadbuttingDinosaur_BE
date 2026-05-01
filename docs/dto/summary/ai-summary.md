# ai DTO summary

> 본 문서는 `docs/dto/dto-overview.md §2 ai` 의 깊이 확장판.
> ⚠ 패키지: `org.example.ai.*` (다른 모듈은 `com.devticket.*`).

## Presentation DTO

### RecommendationRequest (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/req/RecommendationRequest.java`

| 필드명 | 타입 | 검증 |
|---|---|---|
| `userId` | `String` | `@NotBlank` |

### RecommendationResponse (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/res/RecommendationResponse.java`

| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `eventIdList` | `List<String>` |

### ActionLogMessage (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/req/ActionLogMessage.java`

| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `eventId` | `String` |
| `eventIds` | `List<String>` |
| `actionType` | `String` |
| `searchKeyword` | `String` |
| `stackFilter` | `String` |
| `dwellTimeSeconds` | `Integer` |
| `quantity` | `Integer` |
| `totalAmount` | `Long` |
| `timestamp` | `String` |

> ⚠ `ActionLogMessage` 는 ai 측에서 일관되게 사용되는 message schema 인지 확인 필요 — 이전 자동 자산에 단독 등재됐으나 컨트롤러에서 직접 받지 않음. 내부 처리 메시지일 가능성 (Kafka consumer 입력 추정 — 다만 ai 모듈은 Kafka 미사용으로 정합성 ⚠).

## Domain DTO (model)

### UserVector (class)
- source: `ai/src/main/java/org/example/ai/domain/model/UserVector.java`

| 필드명 | 타입 | 의미 |
|---|---|---|
| `userId` | `String` | 사용자 ID |
| `preferenceVector` | `float[]` | 선호 벡터 (기술스택 임베딩 합성 + 행동 누적) |
| `cartVector` | `float[]` | 카트 행동 벡터 |
| `recentVector` | `float[]` | 최근 행동 벡터 (RecentVectorService 가 주기적 재계산) |
| `negativeVector` | `float[]` | 네거티브 시그널 (환불/이탈) 벡터 |

## External (client req/res — ai → 외부 호출용)

### UserTechStackRequest / UserTechStackResponse (record)
- source: `ai/src/main/java/org/example/ai/infrastructure/external/dto/req/UserTechStackRequest.java`
- 호출 대상: `member` 모듈 `getUserTechStack` (`/internal/members/{userId}/tech-stacks`)
- 용도: 콜드스타트 시 사용자 기술스택 임베딩 조회

### PopularEventListRequest / PopularEventListResponse (record)
- source: `ai/src/main/java/org/example/ai/infrastructure/external/dto/req/PopularEventListRequest.java`
- 호출 대상: `event` 모듈 `getPopularEvents` (`/internal/events/popular`)
- 용도: 폴백 / 보강용 인기 이벤트 후보

### ActionLogRequest / ActionLogResponse (record)
- source: `ai/src/main/java/org/example/ai/infrastructure/external/dto/req/ActionLogRequest.java`
- 호출 대상: `log` 모듈 `getRecentActionLog` (Fastify 측 endpoint, ⚠ Java 자동 파서 미커버)
- 용도: 사용자 최근 행동 로그 조회 (RecentVectorService 입력)

## Kafka payload

**없음**. ai 모듈은 Kafka 미사용 (kafka-design.md §3 표에 ai 행 없음).

## ⚠ 미결

- 패키지 일관성: `org.example.ai.*` → `com.devticket.ai.*` 정정 후속 트랙
- `ActionLogMessage` 사용처 / 의미 명확화 (현재 ⚠ 검증 필요 표기)
