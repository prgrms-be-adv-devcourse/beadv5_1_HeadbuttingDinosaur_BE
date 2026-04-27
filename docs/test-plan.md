# 테스트 보강 계획 (Final Integration)

> [requirements-check.md](requirements-check.md) 검증 결과를 기반으로 한 모듈별 핵심 테스트 후보 정리.
> 발표 전 시간 예산에 맞춰 P0 → P1 → P2 순으로 진행.

- **작성일**: 2026-04-27
- **연결 문서**:
  - [requirements-check.md](requirements-check.md) — 요구사항 검증 결과 (12 / 12 🟢, ⚠ 마커 4건)
  - [kafka-design.md](kafka-design.md) — 이벤트 계약
  - [kafka-idempotency-guide.md](kafka-idempotency-guide.md) — Consumer 멱등성 3중 방어선
  - [settlement-process.md](settlement-process.md) — 정산 / 환불 이월 로직

---

## 0. 우선순위 표기

- **P0** — ★ 핵심 플로우 + 현재 테스트 0건 (`⚠ 마커`로 명시). 발표 전 1개라도 필수.
- **P1** — ★ 핵심 플로우 + 매출/정합성 직격 위험. 가능하면 1개씩 추가.
- **P2** — 계약·분기 (무성 실패 방어). 시간 남으면.

---

## 1. P0 — 즉시 보강

### #1. 재고 차감 동시성 테스트 ★ ⚠3

- **모듈**: `event`
- **타깃**: `EventInternalService.adjustStockBulk()` + `Event.deductStock()` (라인 163-184)
- **시나리오**: CountDownLatch + ExecutorService 50~100 스레드 동시 차감 → 정확히 재고 N개만 성공, 나머지 실패 응답
- **검증**: 비관적 락(`@Lock(PESSIMISTIC_WRITE)`, EventRepository.java:63-77) + `@Version`(Event.java:91-92)이 실제로 oversell 방어하는지
- **이유**: requirements-check.md `⚠3`에 명시된 유일한 테스트 갭. 발표에서 "★ #11 동시성 보장"을 주장하려면 이 테스트 1개가 가장 큰 임팩트.
- **소요**: 30분 ~ 1시간 (Spring Boot Test 인프라 기존 활용)

---

## 2. P1 — 가능하면 추가

### #2. 재고 차감 후 Order 저장 실패 → 보상 ★

- **모듈**: `commerce`
- **타깃**: `OrderService.java:651-657` `compensateStock()`
- **시나리오**: 재고 차감 성공 → DB 장애 등으로 Order 저장 실패 → `compensateStock()` 호출되어 재고 복원
- **이유**: TX1(검증) — HTTP 차감 — TX2(저장) 사이 분산 트랜잭션 누수 가능 지점. ★ #11과 함께 결제 성공 후 가장 위험한 구간.

### #3. 환불 멱등성 (`refund.completed` 중복 처리) ★

- **모듈**: `payment`
- **타깃**: `WalletEventConsumer.consumeEventCancelled` (kafka-design.md 라인 698 참조)
- **시나리오**: 동일 `refundId`로 `refund.completed` 이벤트 2회 수신 → 1회만 잔액 복원
- **검증**: kafka-idempotency-guide.md의 3중 방어선(Inbox + DB unique + state guard)이 실제 동작
- **이유**: 환불은 매출 직격, 중복 처리 시 예치금 부풀리기 가능

### #4. 정산 환불 이월 체인 ★

- **모듈**: `settlement`
- **타깃**: `SettlementInternalServiceImpl.processSellerSettlement()` 라인 307-352
- **시나리오**:
  1. 월 정산 1회 → `PENDING_MIN_AMOUNT` 정산서 생성
  2. 다음 달 정산 시 `carriedInAmount` 합산 + `carriedToSettlementId` 체인 설정
  3. 지급 시 원본 + 이월 정산서 양쪽 모두 PAID 상태 전환
- **이유**: settlement-process.md 핵심 로직, 환불 → 정산 정합성 직접 영향

### #5. `payment.failed` Consumer 멱등성

- **모듈**: `event`
- **타깃**: `StockRestoreService.java`
- **시나리오**: 동일 `orderId`로 `payment.failed` 이벤트 2회 수신 → 재고 복원 1회만 발생
- **이유**: 락 충돌 + `@Version` 롤백으로 Kafka 재시도 시 무성 실패 가능성. kafka-idempotency-guide.md의 Consumer 멱등성 가이드 적용 검증.

---

## 3. P2 — 시간 남으면

### #6. AI 콜드스타트 분기 + searchKnn 폴백

- **모듈**: `ai`
- **타깃**: `RecommendationService.java:48-89` (일반) / `:94-155` (콜드스타트)
- **시나리오**:
  - `logWeightSum < 20` → 콜드스타트 진입
  - kNN 결과 5개 미만 → 인기 이벤트 보충 폴백
- **이유**: 분기 검증, 단위 테스트로 충분

### #7. ES `EventDocument` ↔ AI kNN 계약

- **모듈**: 통합 (event ↔ ai)
- **타깃**:
  - `EventDocument.java:25` 주석 (PR #540 계약): `eventId` / `embedding` / `status` 필드
  - `RecommendationService.searchKnn()` includes
- **시나리오**: event가 색인 → ai가 `eventId` 추출 + status=ON_SALE 필터 동작
- **이유**: 필드명 변경 시 무성 실패 가장 위험한 지점 (Kafka 외 ES도 모듈 간 공유 채널)

### #8. JWT 발급-검증 왕복

- **모듈**: `member` + `apigateway`
- **타깃**: `JwtTokenProvider` (발급) + `JwtAuthenticationFilter.java:1-149` (검증)
- **시나리오**:
  - 정상 토큰 → 검증 통과 + 헤더 주입(X-User-Id 등)
  - 만료 토큰 → 401
  - 변조 토큰 → 401

### #9. `event.force-cancelled` 발행 시 ES 상태 동기화

- **모듈**: `event`
- **타깃**: `EventService.forceCancel()` + `syncToElasticsearch()` (라인 402-445)
- **시나리오**: 강제 취소 → DB status `FORCE_CANCELLED` + Outbox 발행 + ES status 동기화

---

## 4. 모듈별 분포

| 모듈 | P0 | P1 | P2 |
|---|---|---|---|
| event | #1 | #5 | #9 |
| commerce | - | #2 | - |
| payment | - | #3 | - |
| settlement | - | #4 | - |
| ai | - | - | #6 |
| event ↔ ai | - | - | #7 |
| member + apigateway | - | - | #8 |

---

## 5. 시간 예산별 추천 시나리오

### A. 1시간 (최소)
- P0 #1 만 추가
- 발표 효과: "★ #11 동시성 보장 → 테스트로 입증" 명시 가능
- requirements-check.md `⚠3` 마커 해소

### B. 3~4시간 (균형)
- P0 #1 + P1 #3 + P1 #4
- 발표 효과: 매출 직격 3축(재고 / 환불 / 정산) 모두 자동 검증
- 발표 시 "핵심 플로우 = 상품선택 → 결제완료 → 환불완료 → 정산완료 → 모두 테스트로 보장" 메시지 강화

### C. 1일 (이상적)
- B + P1 #2 + P1 #5
- 발표 효과: 핵심 플로우 전 구간 + 보상 트랜잭션 + Consumer 멱등성 모두 자동 검증

---

## 6. 작업 컨벤션

- **브랜치**: `test/{module}-{topic}` (예: `test/event-stock-concurrency`)
- **PR 제목**: `[test]({module}): {description}` (예: `[test](event): 재고 차감 동시성 테스트 추가`)
- **커밋 단위**: 테스트 파일 단위 분리
- **PR 본문**:
  - 추가한 테스트 케이스 목록
  - 검증 대상 (이 문서의 #N 참조)
  - 실행 결과 스크린샷 또는 로그 발췌

---

## 7. 미해결 ⚠ 마커 (requirements-check.md에서 인계)

- ⚠1 K8s HPA 매니페스트 — 테스트 범위 외, 별도 인프라 작업
- ⚠2 외부 API 응답 DTO diff — 테스트 범위 외, 발표 시 수동 시연
- ⚠3 동시성 테스트 — **P0 #1로 해소 예정**
- ⚠4 settlement 레거시 (`SettlementItemProcessor.java:19` FEE_RATE 하드코드) — 발표 후 정리
