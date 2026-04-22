# DevTicket — 문서 인덱스

## 1차 진입점

- **[ServiceOverview.md](service/ServiceOverview.md)** — 멘토용 진입점. 9개 모듈 종합

## 모듈 페이지

- [modules/](modules/) — 모듈별 narrative (책임/위임/외부·내부 API/Kafka/DTO/의존성)
  - admin · ai · apigateway · commerce · event · log · member · payment · settlement (9 파일)

## API / DTO 자산

- [api/api-overview.md](api/api-overview.md) — 9 모듈 통합 API 인덱스
- [api/summary/](api/summary/) — 모듈별 endpoint 깊이 카탈로그 (× 9)
- [dto/dto-overview.md](dto/dto-overview.md) — 9 모듈 통합 DTO 인덱스
- [dto/summary/](dto/summary/) — 모듈별 DTO 카탈로그 (필드 표 + source) (× 9)
- [service/service-status.md](service/service-status.md) — Service 메서드 1줄 요약 (인터페이스 기준)

## 횡단 설계 (Kafka)

- [kafka/kafka-design.md](skills/kafka-design.md) — Kafka 토픽 + Saga + ShedLock 횡단 설계
- [kafka/kafka-sync-async-policy.md](skills/kafka-sync-async-policy.md) — 1-A / 1-B / 1-C 분류
- [kafka/kafka-idempotency-guide.md](skills/kafka-idempotency-guide.md) — 멱등성 3중 방어선
- [kafka/actionLog.md](skills/actionLog.md) — Log 모듈 (Fastify/TS)
- [kafka/kafka-impl-plan.md](skills/kafka-impl-plan.md) — 구현 진행

## 문서화 기준

- [standards/api-doc-standard.md](standards/api-doc-standard.md) — API 표 컬럼 / 외부 vs 내부 / 미구현 표기
- [standards/dto-doc-standard.md](standards/dto-doc-standard.md) — DTO/Service 매핑 + 1줄 요약 길이 룰
- [standards/event-schema-standard.md](standards/event-schema-standard.md) — Kafka consumer 표기 + ⚠ 마커 패턴
- [standards/docs-parser-standard.md](standards/docs-parser-standard.md) — 파서 스캔 패턴 + 모듈 커버리지

## 검증 결과

- [requirements-check.md](requirements-check.md) — 기능 요구사항 11개 + 기술스택 6개 + 설계원칙 3개 검증
