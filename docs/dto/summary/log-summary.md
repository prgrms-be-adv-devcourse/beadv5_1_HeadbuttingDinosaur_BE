# log DTO summary

> 본 문서는 `docs/dto/dto-overview.md §6 log` 의 깊이 확장판.
> ⚠ **log 모듈은 Fastify/TypeScript 별도 스택**. Java DTO 0건. TypeScript 측 schema 는 `fastify-log/` 디렉토리 참조.

## Java DTO

**없음** (presentation/dto 0건, messaging/event 0건).

## TypeScript 측 (참고)

log 모듈이 처리하는 메시지 schema:
- **`action.log` (Kafka 1-C)**: 모듈별 발행. CART_ADD/REMOVE (commerce), VIEW/DETAIL_VIEW/DWELL_TIME (event) 등. 발행자 측 DTO 는 `commerce.ActionLogDomainEvent`, `event.ActionLogEvent` 참조.
- **`payment.completed` (Kafka 1-B)**: payment 발행. PURCHASE 액션 INSERT 용. payload 정의는 `payment.PaymentCompletedEvent` (commerce/payment 양쪽 record 동일).

## 관련 자료

- Kafka 메시지 스키마 / 처리 흐름: `docs/kafka/actionLog.md`
- API endpoint: `docs/api/summary/log-summary.md` (HTTP endpoint 0건)
- 발행자 측 DTO: `docs/dto/summary/commerce-summary.md` (action.log) / `docs/dto/summary/payment-summary.md` (payment.completed)
