# DevTicket 부하 테스트 시나리오 명세

> 9개 모듈 MSA의 핵심 경로(인증 / 동시 구매·재고 / 주문→결제 E2E / 검색·추천 / 환불 Saga)에 대한 부하 테스트 시나리오 명세.
> **본 문서는 명세서**다. k6 / JMeter / Gatling 등 구현은 별도 트랙.
>
> 참고: [`ServiceOverview.md`](../ServiceOverview.md) · [`api/api-overview.md`](../api/api-overview.md)

---

## 0. 공통 설정

### 0-1. 진입점 / 인증

- **모든 트래픽 진입점**: `apigateway` (8080). 직접 서비스 포트(8081~8088)를 때리지 않는다 — JWT 검증 / Rate Limit / 라우팅 전부 우회된다.
- **JWT 필터**: `apigateway/.../JwtAuthenticationFilter` — `Authorization: Bearer <token>` 헤더.
- **익명 허용 경로** (`RoutePolicy.AUTH_PUBLIC_PATTERNS`): `/api/auth/**`, `GET /api/events`, `GET /api/events/{id}`, `GET /api/tech-stacks`. 그 외는 401.
- **Rate Limit**: `apigateway/.../RateLimitFilter` 적용 — 단일 IP에서 RPS를 너무 높이면 본 필터에 막혀 백엔드까지 도달하지 못한다. **부하 테스트 클라이언트는 IP 풀(또는 X-Forwarded-For 헤더 우회 가능 여부)을 점검 후 시작**.

### 0-2. 데이터 시드 정책

부하 테스트 시작 전 다음 데이터가 준비되어야 한다.

| 카테고리 | 수량 (권장) | 비고 |
|---|---|---|
| 일반 사용자 | 10,000명 | 비밀번호 동일, 이메일 식별자만 다름. JWT 사전 발급해 캐싱 권장 |
| 판매자 | 50명 | 일부는 이벤트 1~10개씩 보유 |
| 이벤트 (판매중) | 500건 | 그 중 100건은 stockTotal 50, 100건은 stockTotal 500, 나머지는 1,000 이상 |
| 이벤트 (저재고) | 10건 | stockTotal 100. **#11 동시성 시나리오 전용** |
| 예치금 잔액 | 사용자당 1,000,000원 충전 | PG 의존 제거 |

> 시드 스크립트는 `scripts/loadtest-seed/` 아래 별도 트랙. **각 시나리오 실행 전 DB 초기화 → 시드 재실행**을 원칙으로 한다(특히 재고/주문).

### 0-3. 공통 SLO (잠정)

| 지표 | 목표 | 비고 |
|---|---|---|
| p95 latency (읽기) | < 300ms | `GET /api/events`, `GET /api/events/{id}` 등 |
| p95 latency (쓰기) | < 800ms | 주문 생성, 결제 |
| 오류율 (5xx) | < 0.1% | Gateway 5xx 기준 |
| 재고 정합성 | **100%** | 동시성 시나리오는 음수 재고 / 초과 판매 0건 강제 |
| Kafka consumer lag | < 1,000건 (피크 후 30초 내 회복) | `action.log`, `payment.completed` |

---

## 1. 시나리오 A — 인증 (로그인 / 토큰 재발급)

### A-1. 목적
- `member` 모듈 인증 처리량(throughput) 측정
- Gateway JwtAuthenticationFilter 의 검증 비용 측정 (HS256 vs 디코딩 부하)
- Refresh 토큰 재발급 동시성 (Redis / RT 저장소 의존성)

### A-2. 대상 API

| 단계 | HTTP | Path | 인증 |
|---|---|---|---|
| 1 | `POST` | `/api/auth/login` | anon |
| 2 | `POST` | `/api/auth/reissue` | refresh token |
| 3 | `GET`  | `/api/users/me` | access token |
| 4 | `POST` | `/api/auth/logout` | access token |

### A-3. 부하 프로파일

| 단계 | VU | Duration | Ramp |
|---|---|---|---|
| smoke | 1 | 1분 | — |
| baseline | 50 | 5분 | 0→50 (30s) |
| stress | 500 | 10분 | 50→500 (1분), 유지 8분, 500→0 (1분) |
| spike | 1,000 | 3분 | 0→1,000 (5s) 즉시 spike |

### A-4. 검증 포인트

- 로그인 p95 < 500ms, 오류율 0%
- spike 단계에서 토큰 발급 큐 적체 여부 (Member DB 커넥션 풀 포화 / Refresh 저장소 락 경합)
- 잘못된 비밀번호 비율 5% 섞었을 때 401 응답 시간이 정상 응답과 동등한지 (인증 우회 사이드채널 점검)

---

## 2. 시나리오 B — 이벤트 목록 / 상세 (읽기 + ES + 조회수 쓰기)

### B-1. 목적
- ES 기반 `getEventList`(EventController:39) 응답 시간 측정
- 상세 조회의 `logDetailView()` 비동기 이벤트 발행(EventService:157~164) 이 백엔드를 막지 않는지 확인
- 익명 트래픽 — Gateway JWT 우회 경로 처리량

### B-2. 대상 API

| 단계 | HTTP | Path | 비율 |
|---|---|---|---|
| 1 | `GET` | `/api/events?page=0&size=20` | 60% |
| 2 | `GET` | `/api/events?keyword=Spring&category=BACKEND` | 20% |
| 3 | `GET` | `/api/events/{id}` | 18% |
| 4 | `POST` | `/api/events/{id}/dwell` (인증 필요) | 2% — action.log 1-C 발행 부하 측정 |

### B-3. 부하 프로파일

| 단계 | VU | Duration |
|---|---|---|
| baseline | 100 | 10분 |
| stress | 1,000 | 15분 |
| soak | 200 | 60분 (메모리 누수 / ES 커넥션 풀 점검) |

### B-4. 검증 포인트

- 목록 p95 < 300ms, 상세 p95 < 350ms (조회수 증가/이벤트 발행 포함)
- ES 클러스터 CPU < 70%, JVM old-gen 안정
- `action.log` Kafka producer 버퍼 사용량
- soak 60분 동안 Heap 누수, 커넥션 풀 leak 여부

---

## 3. 시나리오 C — 추천 (#9, AI + ES kNN)

### C-1. 목적
- `GET /api/events/user/recommendations` → `event → ai → ES dense_vector kNN` 경로 응답 시간
- AI 모듈 폴백(try-catch) 동작 검증 — ai 다운/지연 시 event 응답이 유지되는가
- `UserVector` 갱신 부하: `action.log` consumer가 추천 결과를 어떻게 흐트러뜨리는지

### C-2. 대상 API

| HTTP | Path | 인증 |
|---|---|---|
| `GET` | `/api/events/user/recommendations` | required |

### C-3. 시나리오 매트릭스

| 케이스 | 설명 |
|---|---|
| C-3-1 | 정상 — ai 모듈 정상, kNN top 30 → 재정렬 top 5 |
| C-3-2 | ai 모듈 지연 주입(toxiproxy 등으로 +500ms) — event 폴백 동작 |
| C-3-3 | ai 모듈 다운 — try-catch 폴백 시 빈 응답 / 인기 이벤트 응답 확인 |
| C-3-4 | Cold-start 사용자 (UserVector 없음) → `recommendByColdStart` 경로 |

### C-4. 부하 프로파일

baseline 50 VU × 10분, stress 300 VU × 10분.

### C-4. 검증 포인트

- 정상 p95 < 600ms, 폴백 시에도 p95 < 800ms (event 모듈 자체 응답 보장)
- ES dense_vector kNN CPU 사용량
- OpenAI 임베딩 호출이 동기 경로에 없는지(있다면 rate limit 위험)

---

## 4. 시나리오 D — 장바구니 + 주문 생성 E2E (정상 흐름)

### D-1. 목적
- 카트 → 주문 → 결제(예치금) → 티켓 발급 전 경로 처리량 측정
- `OrderService.createOrderByCart`(OrderService:106) 내부에서 `event.adjustStockBulk` REST 1-A 동기 호출 비용
- `payment.completed` Outbox(PaymentServiceImpl:200~206) 발행 후 commerce consumer가 `PAID` 전이 + 티켓 발급 + 카트 삭제까지 걸리는 시간 (E2E 지표)

### D-2. 가상 사용자 행동 시퀀스

```
1. POST /api/auth/login                           (or 사전 발급된 토큰 재사용)
2. GET  /api/events?page=0&size=20                ← 1~2회
3. GET  /api/events/{id}                          ← 카트에 담을 이벤트 선택
4. POST /api/cart/items                           {eventId, quantity: 1~3}
   (think time 1~3초)
5. GET  /api/cart
6. POST /api/orders                               (카트 기반 주문 생성)
7. POST /api/payments/wallet ... 예치금 결제 진입점
   ※ 정확한 경로는 payment 모듈 컨트롤러 확인 필요
8. polling GET /api/orders/{orderId}/status       ← `PAID` 까지 최대 5s 폴링
9. GET  /api/tickets                              ← 티켓 발급 확인
```

### D-3. 부하 프로파일

| 단계 | VU | Duration | RPS 목표 (주문 생성) |
|---|---|---|---|
| baseline | 50 | 10분 | 5~10 |
| stress | 300 | 15분 | 30~60 |
| peak | 800 | 5분 | 100+ |

### D-4. 검증 포인트

- 주문 생성 p95 < 800ms (재고 차감 REST 동기 비용 포함)
- 결제 확정 → 티켓 발급 E2E p95 < 3s (Outbox 발행 지연 + consumer 처리 지연)
- 카트가 결제 후 비워졌는지 (`GET /api/cart` 가 비어 있음)
- payment / commerce 모듈 DB 커넥션 풀 포화 시점
- Kafka `payment.completed` consumer lag

---

## 5. 시나리오 E — 동시 구매 / 재고 차감 (#11, **핵심**)

### E-1. 목적

가장 중요한 동시성 시나리오. 시스템이 광고하는 **3중 방어선** 동작 검증:

1. `EventRepository:96` `@Lock(PESSIMISTIC_WRITE)` — DB 행 락
2. `EventInternalService:167~187` 정렬된 ID 기준 단일 쿼리 락 획득(데드락 방지)
3. 결제 실패 시 `payment.failed` / `order.cancelled` → `restoreStockForPaymentFailed` Kafka 보상

**불변식: 어떤 동시성 조건에서도 `event.stockTotal < soldCount` 또는 `stockTotal < 0` 이 발생하면 안 된다.**

### E-2. 시드 / 전제

| 항목 | 값 |
|---|---|
| 대상 이벤트 | 시드 단계에서 만든 "저재고 이벤트" 10건 (각 stockTotal = 100) |
| 가상 사용자 | 1,000명 (예치금 1,000,000원씩 충전된 시드 사용자) |
| 시작 시각 | T0 동시 시작 — 0~100ms jitter |
| 각 사용자 행동 | 1건 무작위 이벤트 선택 → `POST /api/cart/items {qty: 1}` → `POST /api/orders` → 예치금 결제 |

### E-3. 부하 프로파일

| 케이스 | 설명 | 기대 결과 |
|---|---|---|
| E-3-1 정원 매칭 | VU 1,000, 재고 합계 = 1,000 | 정확히 1,000건 성공, 나머지 OUT_OF_STOCK |
| E-3-2 초과 신청 | VU 2,000, 재고 합계 = 1,000 | 정확히 1,000건 성공, 나머지 OUT_OF_STOCK |
| E-3-3 결제 실패 50% 주입 | E-3-2 + 결제 단계 50% 강제 실패 (예치금 부족 사용자 혼입) | 실패만큼 재고 복원, 최종 sold + restored 합산 = 1,000 |
| E-3-4 동시 N장 구매 | VU 500, 각 사용자 qty=3, 재고 합계 1,000 | 333건 성공(qty=3) + 1건 qty=1 → 정확히 1,000장 |

### E-4. 검증 포인트

- **재고 불변식**: 모든 케이스 종료 후 `SELECT stockTotal, soldCount FROM event WHERE id IN (...)` 결과가 정합. 음수 재고 0건.
- 락 대기로 인한 p99 latency tail 측정 (PESSIMISTIC_WRITE 임계)
- 락 순서 고정 위반 시 발생할 데드락 — pg/MySQL 로그에서 deadlock 검출 0건
- 보상(E-3-3): `payment.failed` → `restoreStockForPaymentFailed` 까지 지연 시간 측정
- `OrderService:633~639` 보상 로직 — REST 호출 실패 시 stale 락 누수 여부

### E-5. 실패 케이스 회귀(non-goals 명시)

- 본 시나리오는 **결제 PG 외부 호출 부하**는 측정하지 않는다 (예치금 경로만).
- PG 경로 시나리오는 별도(시나리오 F)로 분리.

---

## 6. 시나리오 F — PG 결제 (선택)

### F-1. 목적
- `readyPayment` → 외부 PG(Toss) → `confirmPgPayment` → `payment.completed` Outbox 경로
- 외부 PG 가 stub/mock 환경일 때만 의미 있음. 실 PG 호출은 부하 테스트 금지.

### F-2. 전제

- Toss mock 서버 또는 wiremock 으로 stubbing — 200ms 지연 추가
- 본 시나리오는 **PG 응답 지연이 시스템 전체 처리량에 미치는 영향** 측정 용도

### F-3. 부하 프로파일
baseline 30 VU × 10분, stress 150 VU × 10분.

### F-4. 검증 포인트
- `payment.completed` 발행 시점(afterCommit) — DB 트랜잭션 커밋 전 발행 0건
- PG 응답 누락 / 재시도 정책 — 멱등성 키 위반 0건
- 스케줄러 fallback (Outbox 발행 누락 사례) 동작

---

## 7. 시나리오 G — 환불 Saga (보상 트랜잭션)

### G-1. 목적

Refund Saga Orchestrator 의 분산 보상 트랜잭션이 부하 상황에서 정합성을 유지하는지 검증.

### G-2. Saga 흐름

```
사용자 환불 요청
   → refund.requested (RefundSagaHandler:29 → RefundSagaOrchestrator.start:67)
      → refund.order.cancel    → refund.order.done/failed
      → refund.ticket.cancel   → refund.ticket.done/failed
      → refund.stock.restore   → refund.stock.done/failed
   → refund.completed (성공) / 보상 체인 (실패)
```

토픽 목록: `KafkaTopics:18~29` 참조.

### G-3. 시드

E-3-1 정원 매칭이 끝난 후 발생한 1,000건의 PAID 주문을 사용.

### G-4. 부하 프로파일

| 케이스 | 설명 |
|---|---|
| G-4-1 | 환불 요청 100건/초 × 10분 — 정상 saga 처리량 측정 |
| G-4-2 | refund.ticket.cancel 단계 50% 실패 주입 — 보상(rollback) 흐름 검증 |
| G-4-3 | refund.stock.restore 단계 timeout — Outbox 재발행 + 멱등성 |

### G-5. 검증 포인트

- 모든 saga 인스턴스가 `refund.completed` 또는 명확한 실패 상태로 종료 (좀비 saga 0건)
- 환불 금액 합 = 예치금 환급 합 (재무 정합성)
- 재고 복원 후 `event.stockTotal` 정합 — soldCount 재계산 일치
- saga 진행 중간에 사용자가 같은 티켓을 재환불 요청 — 멱등성 동작

---

## 8. 시나리오 H — 행동 로그 수집 (Fastify-log) throughput

### H-1. 목적

- `action.log` Kafka 토픽의 consume 처리량 — Fastify-log 모듈이 병목인지 확인
- `payment.completed` 토픽도 동일 consumer 가 수신 (action-log.consumer.ts:15)
- `log.action_log` 테이블 INSERT 처리량 (action-log.service.ts:15)

### H-2. 부하 생성 방식

본 시나리오는 HTTP 부하가 아닌 **Kafka 직접 publish** 또는 `POST /api/events/{id}/dwell` 대량 호출로 생성.

| 케이스 | 생성 방식 |
|---|---|
| H-2-1 | kafka-producer-perf-test 로 `action.log` 토픽 직접 10,000 msg/s 발행 |
| H-2-2 | HTTP `dwell` 1,000 RPS — 실제 경로 |

### H-3. 검증 포인트

- consumer lag — peak 후 30초 내 0 으로 수렴
- PostgreSQL `log.action_log` INSERT TPS, autovacuum 영향
- fastify 모듈 메모리 (Node heap) — pino 로깅이 backpressure 발생시키는지

---

## 9. 측정 / 관측

### 9-1. 필수 메트릭 채널

| 채널 | 도구 |
|---|---|
| 부하 클라이언트 메트릭 | k6 / JMeter / Gatling — RPS, latency 분포, 오류율 |
| Gateway / 모듈 메트릭 | Spring Actuator + Prometheus (`/actuator/prometheus`) — HTTP 요청, DB 풀, JVM |
| Kafka | broker 메트릭 (lag, ISR), consumer-group lag |
| DB | pg_stat_activity, lock 모니터, slow query |
| 인프라 | k3s 노드 CPU/메모리, network |

### 9-2. 시나리오별 PASS/FAIL 게이트

| 시나리오 | PASS 조건 |
|---|---|
| A 인증 | 로그인 p95 < 500ms, 오류율 0% |
| B 목록/상세 | 목록 p95 < 300ms, 상세 p95 < 350ms |
| C 추천 | 정상 p95 < 600ms, 폴백 p95 < 800ms |
| D 주문 E2E | 주문 p95 < 800ms, 결제→티켓 발급 E2E p95 < 3s |
| E 동시성 (#11) | **재고 불변식 100%**, 데드락 0건, 보상 30초 내 |
| F PG | Outbox afterCommit 위반 0건, 멱등성 위반 0건 |
| G 환불 Saga | 좀비 saga 0건, 재무 정합 100% |
| H 로그 | consumer lag 피크 후 30초 내 0 수렴 |

---

## 10. 실행 순서 권장

1. **smoke** — 모든 시나리오를 1 VU × 1분으로 먼저 돌려 회로 자체 점검
2. **B (읽기)** → **A (인증)** — 부하가 가벼운 것부터
3. **D (정상 주문 E2E)** — 시스템 정상 동작 기준선 확보
4. **E (#11 동시성)** — **최우선 검증 시나리오**. 매 변경 시 회귀
5. **C (추천)** / **F (PG)** / **G (환불)** — 부가 경로
6. **H (로그 throughput)** — 백그라운드 부하로 다른 시나리오와 겹쳐 실행 가능

---

## 11. Out of Scope (이번 명세에서 제외)

- 실 외부 PG 호출 부하 — 금융사 정책 위반 가능
- 관리자 전용 endpoint(`/api/admin/**`) — 트래픽 패턴 무의미
- 정산 Batch — 스케줄 기반, 부하 모델 별도
- OAuth 외부 IdP(Google) 직접 부하 — Google 정책 위반
