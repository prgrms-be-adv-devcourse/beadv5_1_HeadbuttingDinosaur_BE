# DevTicket — 문서 인덱스

> 발표 Q&A 안전망 문서 모음. P5 트랙(2026-04-27 ~ 04-28) 산출물 + 기존 자산.

## 1차 진입점

- **[ServiceOverview.md](ServiceOverview.md)** — 멘토용 진입점. 모듈 9개 + 마커 8건 + 시스템 한계 4건 + Q&A 8건

## 자동 생성 자산

- [api/api-overview.md](api/api-overview.md) — 모듈별 외부/내부 API 표
- [dto/dto-overview.md](dto/dto-overview.md) — DTO 목록
- [service/service-status.md](service/service-status.md) — Service 메서드 1줄 요약 (HIGH 처리 27건 / 인터페이스 기준)
  - ⚠ event/member 모듈 미등재 → [standards/docs-parser-standard.md](standards/docs-parser-standard.md)

## 횡단 설계

- [kafka/kafka-design.md](kafka/kafka-design.md) — Kafka 토픽 + Saga + ShedLock 횡단 설계
- [kafka/kafka-sync-async-policy.md](kafka/kafka-sync-async-policy.md) — 1-A / 1-B / 1-C 분류
- [kafka/kafka-idempotency-guide.md](kafka/kafka-idempotency-guide.md) — 멱등성 3중 방어선
- [kafka/actionLog.md](kafka/actionLog.md) — Log 모듈 (Fastify/TS)
- [kafka/kafka-impl-plan.md](kafka/kafka-impl-plan.md) — 구현 진행 + 미결사항

## 문서화 기준

- [standards/dto-doc-standard.md](standards/dto-doc-standard.md) — DTO/Service 매핑 + 1줄 요약 길이 룰
- [standards/event-schema-standard.md](standards/event-schema-standard.md) — Kafka consumer 표기 + ⚠ 마커 패턴 A/B
- [standards/docs-parser-standard.md](standards/docs-parser-standard.md) — 파서 스캔 패턴 (미해결)
- [standards/api-doc-standard.md](standards/api-doc-standard.md) — API 표 컬럼 / 외부 vs 내부 / 미구현 표기 (골격, 발표 후 본문 보강)

## 검증 결과

- [requirements-check.md](requirements-check.md) — 세미 12개 + 기술스택 6개 + 설계원칙 3개 검증

> 모듈 페이지 `modules/` 디렉토리는 미작성. 모듈별 정보는 ServiceOverview.md §3에 통합.
