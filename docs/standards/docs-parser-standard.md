# docs parser 정리 기준

> 자동 생성 자산(`api-overview`, `dto-overview`, `service-status` 등) 의 파서/생성기 동작 규칙.

## 스캔 대상 디렉토리 패턴

자동 생성기는 다음 위치를 스캔한다:
- API: `**/presentation/controller/*Controller.java` 의 `@RequestMapping` / 메서드 매핑
- DTO: `**/presentation/dto/{req,res}/**/*.java` 의 record / class 정의
- Kafka payload: `**/messaging/event/**/*.java` (수동 정리 — 자동 파서 범위 외)

⚠ 일부 모듈의 `application/*Service*.java` 직속 패턴은 자동 파서가 누락 가능 (event/member 모듈 `service-status.md` 정당 누락 사유).

## 산출물 구조

```
docs/api/
├── api-overview.md                    (9 모듈 통합 인덱스, schema v2)
├── api-overview.json                  (9 모듈 통합 메타)
└── summary/
    └── {module}-summary.md / .json    (모듈별 깊이 카탈로그) × 9

docs/dto/
├── dto-overview.md                    (9 모듈 통합 인덱스, schema v2)
├── dto-overview.json                  (9 모듈 통합 메타)
└── summary/
    └── {module}-summary.md / .json    (모듈별 깊이 카탈로그) × 9
```

## 모듈 커버리지

| 모듈 | API | DTO | 비고 |
|---|---|---|---|
| admin | ✅ | ✅ | |
| ai | ✅ | ✅ | 패키지 `org.example.ai.*` (다른 모듈은 `com.devticket.*`) |
| apigateway | ✅ (health 1건) | (DTO 0건) | 라우팅 / filter chain 전용 |
| commerce | ✅ | ✅ | Kafka payload 23건 별도 |
| event | ✅ | ✅ | Kafka payload 10건 별도 |
| log | (Fastify 별도 스택) | (Java DTO 0건) | `fastify-log/` TypeScript |
| member | ✅ | ✅ | 모듈 중 DTO 최다 (40건) |
| payment | ✅ | ✅ | 외부 PG (Toss) DTO 별도 |
| settlement | ✅ | ✅ | Spring Batch step 입출력 DTO 별도 |

## ⚠ 마커 사용 규칙

`event-schema-standard.md §⚠ 마커 사용 구분` 와 동일 패턴 적용:
- **A** (후행 ⚠): 정상 동작하나 추가 컨텍스트 필요
- **B** (`⚠ 확인 필요:`): stub / 비활성 / 미구현
- **C** (드리프트, 비공식): 코드와 문서 표기 불일치

자동 자산 drift 는 모듈 페이지에서 ⚠ 마커로만 노출. 자동 수정 금지 (CLAUDE.md §8).
