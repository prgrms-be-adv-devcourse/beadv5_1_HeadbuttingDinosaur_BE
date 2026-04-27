# 🤖 DevTicket Log Module — PR Review Agent

> 이 문서는 Codex가 **로그 모듈** PR 리뷰를 수행할 때 참조하는 규칙 명세입니다.
> 로그 모듈은 Node.js + Fastify + TypeScript 기반이므로, Java/Spring 공통 agent.md와 분리하여 관리합니다.
> 모든 리뷰는 한국어로 작성합니다.

---

## 1. 모듈 개요

- **모듈명:** devticket-log
- **역할:** Kafka `action.log` 토픽을 consume하여 PostgreSQL `log.action_log` 테이블에 저장하는 경량 로그 수집기
- **기술 스택:** Node.js 21+, Fastify, TypeScript (strict), KafkaJS, pg (node-postgres), pino
- **포트:** 8086
- **특성:** 외부 API는 health check + 내부 조회용 `/internal/logs/*`만 노출. Gateway는 `/api/logs/**`를 본 모듈로 라우팅하나 현재 매핑된 외부 엔드포인트는 없음. `/internal/*`는 `X-Internal-Service` 헤더 기반 서비스 간 호출 (allowlist: `ai`).

### 프로젝트 전체 서비스 맥락

| 서비스 | 포트 | 스택 | 역할 |
|--------|------|------|------|
| Gateway | 8080 | Spring Cloud Gateway | JWT 검증, 라우팅 |
| Member | 8081 | Spring Boot | 회원/인증/프로필 |
| Event | 8082 | Spring Boot | 이벤트 CRUD, 재고 |
| Commerce | 8083 | Spring Boot | 장바구니, 주문, 티켓 |
| Payment | 8084 | Spring Boot | PG/예치금 결제, 환불 |
| Settlement | 8085 | Spring Boot | 정산 |
| **Log** | **8086** | **Node.js + Fastify** | **행동 로그 수집 + 추천용 행동 조회** |
| Admin | 8087 | Spring Boot | 운영 관리 |
| AI | 8088 | Spring Boot | 임베딩, 추천 (`ai/` 모듈 — Java/Spring. 게이트웨이 외부 라우팅 없음, 내부 호출 전용) |

---

## 2. 디렉토리 구조

```
devticket-log/
├── src/
│   ├── index.ts                     # Fastify 부트스트랩 + graceful shutdown
│   ├── config/
│   │   ├── env.ts                   # 환경변수 로드 및 검증
│   │   ├── database.ts              # PostgreSQL 커넥션 풀
│   │   └── kafka.ts                 # KafkaJS consumer 설정
│   ├── consumer/
│   │   └── action-log.consumer.ts   # action.log + payment.completed 토픽 consume (topic 분기)
│   ├── service/
│   │   ├── action-log.service.ts        # 검증/변환/저장 + 최근 로그 조회
│   │   └── payment-completed.service.ts # payment.completed → PURCHASE 로그 변환·저장
│   ├── repository/
│   │   └── action-log.repository.ts # PostgreSQL INSERT / SELECT
│   ├── model/
│   │   ├── action-log.model.ts          # ActionLog 인터페이스
│   │   ├── action-type.enum.ts          # ActionType enum
│   │   └── payment-completed.model.ts   # payment.completed 메시지 스키마
│   ├── route/
│   │   ├── health.route.ts          # GET /health, GET /ready
│   │   └── internal-log.route.ts    # GET /internal/logs/actions (X-Internal-Service 인증)
│   └── util/
│       └── logger.ts                # pino 로거
├── test/
│   ├── unit/
│   └── integration/
├── sql/
│   └── V1__create_action_log.sql
├── Dockerfile
├── package.json
├── tsconfig.json
├── .env.example
└── README.md
```

---

## 3. 계층 규칙 — ⚠️ 최우선 검증 대상

```
route / consumer → service → repository
                                 ↓
                            config (env, database, kafka)
                            model (어디에도 의존 안 함)
```

**위반 시 반드시 리뷰 코멘트를 남겨야 하는 규칙:**

| 규칙 | 위반 사례 |
|------|----------|
| consumer는 service만 호출 | consumer에서 `pool.query()` 직접 실행 |
| service는 repository만 호출 | service에서 `pg.Pool` 직접 import |
| repository에 비즈니스 로직 금지 | repository에서 actionType 검증 |
| model은 어디에도 의존하지 않음 | model에서 `pg`, `kafkajs` import |
| route에 비즈니스 로직 금지 | health route에서 DB INSERT |

---

## 4. 코드 컨벤션

### 4.1 TypeScript 기본

| 규칙 | 설정 |
|------|------|
| strict 모드 | `"strict": true` 필수 |
| 들여쓰기 | 스페이스 2칸 (탭 금지) |
| 세미콜론 | 사용 |
| 문자열 | 작은따옴표 (`'`) |
| 최대 줄 길이 | 120자 |
| 후행 쉼표 | 사용 (trailing comma) |

### 4.2 네이밍 규칙

**파일 네이밍:**

| 대상 | 규칙 | 올바른 예시 | 잘못된 예시 |
|------|------|------------|------------|
| 모든 파일 | kebab-case | `action-log.service.ts` | `actionLogService.ts` |
| 테스트 파일 | `{대상}.test.ts` | `action-log.service.test.ts` | `actionLogServiceTest.ts` |
| 통합 테스트 | `{대상}.integration.test.ts` | `action-log.consumer.integration.test.ts` | — |

**코드 네이밍:**

| 대상 | 규칙 | 올바른 예시 | 잘못된 예시 |
|------|------|------------|------------|
| 클래스 / 인터페이스 | PascalCase | `ActionLogService` | `actionLogService` |
| 함수 / 변수 | camelCase | `saveActionLog` | `save_action_log` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` | `maxRetryCount` |
| Enum 이름 | PascalCase | `ActionType` | `ACTION_TYPE` |
| Enum 값 | UPPER_SNAKE_CASE | `DETAIL_VIEW` | `DetailView` |
| boolean | `is` / `has` 접두사 | `isValid` | `valid` |
| 약어 금지 | — | `quantity` | `qty` |

**함수 접두사:**

| 동작 | 접두사 | 예시 |
|------|--------|------|
| 저장 | `save` / `insert` | `saveActionLog()`, `insertActionLog()` |
| 조회 | `get` / `find` | `getById()` |
| 검증 | `validate` | `validateMessage()` |
| 변환 | `toModel` / `from` | `toActionLog()` |
| 처리 | `handle` / `process` | `handleMessage()` |
| 존재 확인 | `exists` / `is` | `isValidActionType()` |

### 4.3 import 정렬

```typescript
// 1. Node.js 내장 모듈

// 2. 외부 라이브러리
import Fastify from 'fastify';
import { Kafka } from 'kafkajs';
import { Pool } from 'pg';

// 3. 프로젝트 내부 — config
import { env } from '../config/env';
import { pool } from '../config/database';

// 4. 프로젝트 내부 — model
import { ActionType } from '../model/action-type.enum';
import { ActionLog } from '../model/action-log.model';

// 5. 프로젝트 내부 — 같은 계층 또는 하위 계층
import { actionLogRepository } from '../repository/action-log.repository';
```

→ 와일드카드 import (`import * as`) 지양. 명시적 named import 사용.

### 4.4 타입 규칙

| 규칙 | 허용 | 금지 |
|------|------|------|
| `any` | ❌ | `any` 사용 시 반드시 지적 |
| `unknown` | ✅ | 외부 입력(Kafka 메시지) 수신 시 사용 |
| 함수 반환 타입 | 명시 필수 | 추론에만 의존 |
| 데이터 구조 | `interface` | `type` (유니온/유틸리티에만 type 허용) |
| 타입 단언 (`as`) | 최소화 | 타입 가드 함수 권장 |

```typescript
// ✅ 올바른 패턴 — 타입 가드
function isValidActionType(value: string): value is ActionType {
  return Object.values(ActionType).includes(value as ActionType);
}

// ❌ 잘못된 패턴 — as 남용
const actionType = rawMessage.actionType as ActionType; // 검증 없이 단언
```

### 4.5 export 규칙

- 함수/객체 단위 named export 사용
- `export default` 사용 금지 (import 시 이름 불일치 방지)

```typescript
// ✅ named export
export function saveActionLog(log: ActionLog): Promise<void> { ... }
export const actionLogRepository = { insertActionLog };

// ❌ default export
export default class ActionLogService { ... }
```

---

## 5. 계층별 코드 작성 규칙

### 5.1 Consumer (consumer/)

- Kafka 메시지 수신의 진입점. Spring의 Controller/Listener 역할.
- **service만 호출** — repository, database 직접 접근 금지
- 에러 발생 시 **절대 throw 하지 않음** → 로그 + commit (skip)
- action.log 저장 실패는 핵심 기능에 영향 없어야 함

```typescript
// ✅ 올바른 consumer 패턴
const handleMessage = async (message: KafkaMessage): Promise<void> => {
  try {
    const raw = message.value?.toString();
    if (!raw) {
      logger.warn('빈 메시지 수신 — skip');
      return;
    }
    const parsed = JSON.parse(raw);
    await actionLogService.save(parsed);
  } catch (error) {
    logger.error({ error, offset: message.offset }, 'action log 처리 실패 — skip');
    // throw 하지 않음 → 무한 재처리 방지
  }
};

// ❌ 잘못된 패턴
const handleMessage = async (message: KafkaMessage): Promise<void> => {
  const parsed = JSON.parse(message.value!.toString()); // null 체크 없음
  await pool.query(INSERT_SQL, [parsed.userId, ...]);   // repository 건너뜀
  throw new Error('처리 실패');                          // consumer에서 throw
};
```

**리뷰 체크리스트:**
- [ ] consumer에서 repository/database를 직접 호출하지 않는가?
- [ ] 모든 에러가 catch되어 throw 없이 로그 + skip 처리되는가?
- [ ] null/undefined 메시지에 대한 방어 코드가 있는가?

### 5.2 Service (service/)

- 비즈니스 로직 담당: 메시지 검증, 모델 변환, repository 위임
- repository만 호출 — database(pool) 직접 접근 금지
- 검증 실패 시 에러를 throw해도 됨 (consumer에서 catch)

```typescript
// ✅ 올바른 service 패턴
export async function save(raw: unknown): Promise<void> {
  const message = validateAndParse(raw);
  const actionLog = toActionLog(message);
  await actionLogRepository.insertActionLog(actionLog);
}

function validateAndParse(raw: unknown): ActionLogMessage {
  if (!raw || typeof raw !== 'object') {
    throw new Error('유효하지 않은 메시지 형식');
  }
  const msg = raw as Record<string, unknown>;
  if (!msg.userId || typeof msg.userId !== 'string') {
    throw new Error('userId 누락 또는 잘못된 타입');
  }
  if (!msg.actionType || !isValidActionType(msg.actionType as string)) {
    throw new Error(`유효하지 않은 actionType: ${msg.actionType}`);
  }
  // ...
  return msg as ActionLogMessage;
}
```

**리뷰 체크리스트:**
- [ ] service에서 `pg.Pool`이나 `pool.query()`를 직접 사용하지 않는가?
- [ ] actionType 검증이 ActionType enum 기반으로 수행되는가?
- [ ] userId 필수값 검증이 있는가?

### 5.3 Repository (repository/)

- PostgreSQL 쿼리 실행만 담당
- 비즈니스 로직 금지 (검증, 변환 등 service에서 처리)
- SQL 쿼리는 **상수로 분리**, **파라미터 바인딩 필수**

```typescript
// ✅ 올바른 repository 패턴
const INSERT_ACTION_LOG = `
  INSERT INTO log.action_log
    (user_id, event_id, action_type, search_keyword, stack_filter,
     dwell_time_seconds, quantity, total_amount, created_at)
  VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
`;

export async function insertActionLog(log: ActionLog): Promise<void> {
  await pool.query(INSERT_ACTION_LOG, [
    log.userId, log.eventId, log.actionType, log.searchKeyword,
    log.stackFilter, log.dwellTimeSeconds, log.quantity, log.totalAmount,
  ]);
}

// ❌ 문자열 연결 — SQL 인젝션 취약
await pool.query(`INSERT INTO log.action_log (user_id) VALUES ('${userId}')`);

// ❌ repository에 비즈니스 로직
export async function insertActionLog(log: ActionLog): Promise<void> {
  if (log.actionType === 'PURCHASE' && !log.totalAmount) { // 여기서 하면 안 됨
    throw new Error('PURCHASE에는 totalAmount 필수');
  }
  // ...
}
```

**리뷰 체크리스트:**
- [ ] SQL 쿼리에 문자열 연결(`${}`/`+`)이 사용되지 않았는가?
- [ ] 모든 쿼리가 파라미터 바인딩(`$1, $2, ...`)을 사용하는가?
- [ ] SQL 쿼리 문자열이 상수로 분리되어 있는가?
- [ ] repository에 if/else 분기나 검증 로직이 없는가?

### 5.4 Model (model/)

- 타입 정의만 포함 — 외부 의존성 import 금지
- `interface`로 데이터 구조 정의
- `enum`으로 상수 집합 정의

### 5.5 Route (route/)

- 외부 노출은 health check 만, 그 외 `/internal/*` 는 서비스 간 호출 전용
- `/internal/*` 는 `X-Internal-Service` 헤더 필수 + allowlist 검증 (`internal-log.route.ts:6` `INTERNAL_SERVICE_ALLOWLIST` 참고)
- 비즈니스 로직 금지 — 검증·조회는 service 위임

### 5.6 Config (config/)

- 환경변수 로드, DB 풀, Kafka 인스턴스 설정
- `.env` 파일에 시크릿 하드코딩 금지
- 환경변수 누락 시 즉시 프로세스 종료

---

## 6. 에러 처리 규칙

### 6.1 핵심 원칙

> **action.log 저장 실패가 다른 서비스의 핵심 기능에 영향을 주면 안 된다.**

| 상황 | 처리 |
|------|------|
| Kafka 메시지 JSON 파싱 실패 | 로그 기록 + skip (commit) |
| actionType 유효하지 않음 | 로그 기록 + skip (commit) |
| 필수 필드 누락 (userId 등) | 로그 기록 + skip (commit) |
| DB INSERT 실패 | 로그 기록 + skip (commit) |
| DB 커넥션 풀 고갈 | 에러 로그 + readiness probe fail → 재시작 |
| Kafka broker 연결 끊김 | KafkaJS 자동 재연결 + 에러 로그 |

### 6.2 로깅 규칙

- `console.log` / `console.error` **금지** → pino 로거 사용
- 로그 레벨: `debug`, `info`, `warn`, `error`
- 에러 로그에 context 포함 (어떤 메시지의 어떤 offset에서 실패했는지)
- **민감 정보 로그 출력 금지** (user_id UUID는 허용, 개인정보는 금지)

```typescript
// ✅ 올바른 로깅
logger.error({ error, offset: message.offset, actionType: parsed?.actionType },
  'action log 저장 실패');

logger.info({ actionType: log.actionType, userId: log.userId },
  'action log 저장 완료');

// ❌ 잘못된 로깅
console.log('저장 완료');                    // console 금지
logger.info({ message: rawMessageString });  // 전체 메시지 원본 노출 주의
```

**리뷰 체크리스트:**
- [ ] `console.log` / `console.error`가 사용되지 않았는가?
- [ ] 에러 로그에 offset 등 디버깅 context가 포함되어 있는가?
- [ ] 로그에 민감 정보가 출력되지 않는가?

---

## 7. Kafka Consumer 규칙

### 7.1 Consumer 설정

| 설정 | 값 | 사유 |
|------|----|------|
| `groupId` | `log-group` | AI 모듈(`ai-group`)과 독립 consume |
| `fromBeginning` | `false` | 기존 메시지 재처리 불필요 |
| `autoCommit` | `false` | 저장 성공 후 수동 commit |
| `sessionTimeout` | `30000` | 기본값 유지 |

### 7.2 메시지 처리 흐름

```
Kafka message 수신
  → null/empty 체크 (실패 → 로그 + skip)
  → JSON.parse (실패 → 로그 + skip)
  → service.save 호출
     → actionType 검증 (실패 → throw → consumer catch → 로그 + skip)
     → ActionLog 모델 변환
     → repository.insertActionLog (실패 → throw → consumer catch → 로그 + skip)
  → 수동 commit
```

### 7.3 토픽 및 Producer/Consumer 매핑 (전체 프로젝트)

| 토픽명 | Producer | Consumer(s) | 설명 |
|--------|----------|-------------|------|
| `payment.completed` | Payment | Commerce, Event, **Log** | 결제 완료 후속 처리. Log 모듈은 PURCHASE 행동 로그로 변환 저장 |
| `refund.completed` | Payment | Commerce, Event, Payment(Wallet) | 환불 후속 처리 |
| `event.force-cancelled` | Event | Payment | 강제 취소 → 일괄 환불 |
| `event.sale-stopped` | Event | Payment | 판매 중지 → 일괄 환불 |
| `member.suspended` | Member | Commerce, Member(Token) | 회원 제재 후속 처리 |
| **`action.log`** | **Event, Commerce, Payment** *(Producer 미구현 — 설계상 발행 주체)* | **Log Module** | **행동 로그 수집. AI 모듈은 직접 구독 대신 `/internal/logs/actions`로 조회** |

### 7.4 action.log 메시지 스키마

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "actionType": "PURCHASE",
  "searchKeyword": null,
  "stackFilter": null,
  "dwellTimeSeconds": null,
  "quantity": 2,
  "totalAmount": 100000,
  "timestamp": "2025-08-15T14:30:00"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | UUID string | ✅ | 사용자 외부 식별키 |
| eventId | UUID string | ❌ | 이벤트 외부 식별키. VIEW 시 NULL 가능 |
| actionType | string | ✅ | ActionType enum 값 |
| searchKeyword | string | ❌ | 검색어 |
| stackFilter | string | ❌ | 기술 스택 필터 |
| dwellTimeSeconds | int | ❌ | 체류 시간. DWELL_TIME 시 필수 |
| quantity | int | ❌ | 수량. PURCHASE, CART_ADD 시 |
| totalAmount | long | ❌ | 금액. PURCHASE, REFUND 시 |
| timestamp | datetime string | ✅ | 발행 시각 |

### 7.5 리뷰 체크리스트

- [ ] `groupId`가 `log-group`인가?
- [ ] `autoCommit`이 `false`이고, 처리 완료 후 수동 commit하는가?
- [ ] consumer에서 throw로 인한 무한 재처리 가능성이 없는가?
- [ ] Graceful shutdown 시 `consumer.disconnect()`가 호출되는가?

---

## 8. PostgreSQL 규칙

### 8.1 스키마 격리

- 스키마명: `log`
- 다른 서비스 스키마 접근 **절대 금지** (member, event, commerce 등)
- 커넥션 풀: `pg.Pool`, `max: 10` 이하 (경량 서비스)

### 8.2 쿼리 규칙

- **파라미터 바인딩 필수** (`$1, $2, ...`) — SQL 인젝션 방지
- ORM 사용 안 함 — raw query (`pool.query`)
- 쿼리 문자열은 **상수로 분리** (함수 내 인라인 금지)
- `action_type`은 DB에 VARCHAR로 저장 (PostgreSQL ENUM 타입 사용 안 함 — 확장성)

### 8.3 action_log 테이블

```sql
CREATE TABLE log.action_log (
  id                 BIGSERIAL    PRIMARY KEY,
  user_id            UUID         NOT NULL,
  event_id           UUID,
  action_type        VARCHAR(20)  NOT NULL,
  search_keyword     VARCHAR(255),
  stack_filter       VARCHAR(255),
  dwell_time_seconds INT,
  quantity           INT,
  total_amount       BIGINT,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

---

## 9. 환경변수 규칙

- `.env` 파일에 시크릿 하드코딩 금지 (DB 비밀번호, API 키)
- `env.ts`에서 로드 + 타입 검증 + 누락 시 즉시 `process.exit(1)`
- 네이밍: `UPPER_SNAKE_CASE`

### 필수 환경변수

```
DB_HOST          # PostgreSQL 호스트
DB_PORT          # PostgreSQL 포트
DB_NAME          # 데이터베이스명
DB_USER          # DB 사용자
DB_PASSWORD      # DB 비밀번호
DB_SCHEMA=log    # 스키마명
KAFKA_BROKERS                   # Kafka 브로커 (쉼표 구분)
KAFKA_GROUP_ID=log-group
KAFKA_TOPIC_ACTION_LOG=action.log              # VIEW/DETAIL_VIEW/CART_*/DWELL_TIME/REFUND
KAFKA_TOPIC_PAYMENT_COMPLETED=payment.completed # PURCHASE 로그 변환용
PORT=8086
NODE_ENV                        # development | production
LOG_LEVEL=info                  # pino 로그 레벨
```

**리뷰 체크리스트:**
- [ ] `.env`, `env.ts`, 코드 내에 비밀번호/키가 하드코딩되어 있지 않은가?
- [ ] 새 환경변수 추가 시 `.env.example`에도 반영되었는가?
- [ ] 환경변수 누락 시 즉시 종료 처리되는가?

---

## 10. 테스트 규칙

### 10.1 프레임워크

- **Vitest** 사용 (Jest 호환, ESM 네이티브 지원)

### 10.2 파일 네이밍

```
{대상}.test.ts               → 단위 테스트
{대상}.integration.test.ts   → 통합 테스트
```

### 10.3 테스트 구조 — Given-When-Then

```typescript
describe('ActionLogService', () => {
  describe('save', () => {
    it('유효한 DETAIL_VIEW 메시지를 저장한다', async () => {
      // given
      const message = {
        userId: 'some-uuid',
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockRepository.insertActionLog).toHaveBeenCalledWith(
        expect.objectContaining({ actionType: 'DETAIL_VIEW' }),
      );
    });

    it('유효하지 않은 actionType이면 에러를 던진다', async () => {
      // given
      const message = { userId: 'uuid', actionType: 'INVALID' };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow();
    });
  });
});
```

### 10.4 리뷰 체크리스트

- [ ] 새 기능에 대한 테스트가 존재하는가?
- [ ] Given-When-Then 구조를 따르는가?
- [ ] 정상 케이스 + 에러 케이스가 모두 커버되는가?
- [ ] mock 대상이 적절한가? (repository mock, pool mock)

---

## 11. Docker / 배포 규칙

### 11.1 Dockerfile

- 베이스: `node:21-alpine`
- 멀티스테이지 빌드 (build → production)
- 프로덕션 의존성만 포함 (`npm ci --omit=dev`)
- HEALTHCHECK: `curl -f http://localhost:8086/health || exit 1`

### 11.2 k8s 리소스 제한

```yaml
resources:
  requests:
    memory: "64Mi"
    cpu: "50m"
  limits:
    memory: "128Mi"
    cpu: "200m"
```

### 11.3 Health Check 엔드포인트

| 경로 | 용도 | 정상 응답 | 실패 응답 |
|------|------|----------|----------|
| `GET /health` | Liveness probe | `200 { status: 'ok' }` | — (프로세스 죽음) |
| `GET /ready` | Readiness probe | `200 { status: 'ready' }` | `503 { status: 'not ready' }` |

- `/ready`는 DB ping + Kafka consumer 상태 확인

---

## 12. Git 컨벤션

### 12.1 브랜치

```
feat/log-{기능}
fix/log-{수정내용}
chore/log-{설정}
test/log-{테스트대상}
```

### 12.2 커밋 메시지

```
{type}(log): {설명}
```

| type | 용도 | 예시 |
|------|------|------|
| `feat` | 새 기능 | `feat(log): Kafka action.log consumer 구현` |
| `fix` | 버그 수정 | `fix(log): NULL eventId 처리 누락 수정` |
| `test` | 테스트 | `test(log): ActionLogService 단위 테스트 추가` |
| `chore` | 빌드/설정 | `chore(log): Dockerfile 작성` |
| `refactor` | 리팩토링 | `refactor(log): consumer 에러 핸들링 개선` |
| `docs` | 문서 | `docs(log): README 작성` |

### 12.3 PR 제목

```
[Log] {type}: {설명}
```

예시: `[Log] feat: Kafka action.log consumer 구현`

### 12.4 PR 본문

```markdown
## 관련 이슈
- close #이슈번호

## 작업 내용
- 구현/수정한 내용

## 변경 사항
- 변경된 파일/모듈

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과 (해당 시)

## 참고 사항
- 리뷰어가 알아야 할 내용
```

---

## 13. Enum / 상태값 정의

PR에서 ActionType 값을 사용하는 경우, 아래 정의와 반드시 일치해야 한다.

### ActionType

| 값 | 설명 | 발행 서비스 | 발행 시점 |
|----|------|-----------|----------|
| `VIEW` | 이벤트 목록 조회 | Event Service | 목록 조회 API 내부 |
| `DETAIL_VIEW` | 이벤트 상세 조회 | Event Service | 상세 조회 API 내부 |
| `CART_ADD` | 장바구니 담기 | Commerce Service | 장바구니 담기 API 내부 |
| `CART_REMOVE` | 장바구니 삭제 | Commerce Service | 장바구니 삭제 API 내부 |
| `PURCHASE` | 구매 완료 | Payment Service | 결제 승인 성공 시 |
| `DWELL_TIME` | 체류 시간 기록 | Event Service | 프론트 이탈 시 호출 |
| `REFUND` | 환불 완료 | Payment Service | 환불 COMPLETED 전이 시 |

---

## 14. 보안 검증

- [ ] SQL 쿼리에 문자열 연결이 사용되지 않았는가? (파라미터 바인딩 필수)
- [ ] `.env`, 코드 내에 비밀번호/API 키가 하드코딩되어 있지 않은가?
- [ ] `console.log` / `console.error`가 사용되지 않았는가? (pino 사용)
- [ ] 로그에 민감 정보(개인정보, 비밀번호, 토큰)가 출력되지 않는가?
- [ ] `node_modules`가 `.gitignore`에 포함되어 있는가?

---

## 15. 리뷰 코멘트 작성 가이드

### 심각도 분류

| 접두사 | 의미 | 예시 |
|--------|------|------|
| `🚨 [CRITICAL]` | 반드시 수정 | 계층 위반, SQL 인젝션, `any` 남용, consumer에서 throw |
| `⚠️ [WARNING]` | 수정 권장 | `console.log`, autoCommit true, 반환 타입 누락, 환경변수 하드코딩 |
| `💡 [SUGGESTION]` | 개선 제안 | 네이밍 개선, 함수 분리, 테스트 보강 |
| `❓ [QUESTION]` | 의도 확인 | 설계 선택 의도 질문 |

### 코멘트 포맷

```
{접두사} {위반 내용 한 줄 요약}

**근거:** {이 문서의 관련 규칙 섹션}
**현재 코드:** {문제 코드 또는 설명}
**수정 제안:** {올바른 코드 또는 방향}
```

---

## 16. 리뷰 우선순위

1. **계층 위반** — consumer → service → repository 의존 방향
2. **보안** — SQL 인젝션, 환경변수 하드코딩, console 사용
3. **Kafka 처리** — commit 방식, 에러 핸들링 (throw 금지), groupId
4. **타입 안전성** — `any` 사용, 반환 타입 누락, 타입 단언 남용
5. **에러 처리** — consumer throw 여부, 로그 context 포함
6. **코드 컨벤션** — 네이밍, import 정렬, export 규칙
7. **테스트** — 존재 여부, given-when-then, 에러 케이스 커버
8. **Git 컨벤션** — PR 제목, 커밋 메시지
