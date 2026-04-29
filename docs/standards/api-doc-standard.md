# API 문서화 기준

> `docs/api/` 자동 생성 자산(`api-overview.md`, `api-summary.md`, `api-overview.json`)을 큐레이션할 때 따르는 규칙.

## 1. API 표 컬럼 순서

표준 컬럼 (api-overview.md 기준):

```
영역 | 메서드 | 경로 | Controller 메서드 | 요청 DTO | 응답 DTO | 설명
```

### 컬럼 작성 규칙

| 컬럼 | 규칙 |
|---|---|
| 영역 | 모듈 내 도메인 (`Cart`, `Order`, `Ticket`). 내부 API는 `Order Internal` 같이 접미사 |
| 메서드 | HTTP 메서드 대문자 (`GET` / `POST` / `PATCH` / `DELETE`) |
| 경로 | PathVariable은 `{중괄호}` 표기. 정규식 제약은 그대로 보존 (예: `/api/seller/settlements/{yearMonth:[0-9]{6}}`) |
| Controller 메서드 | api-overview.md는 백틱 메서드명(`addToCart`). api-summary.md는 `{ClassName}#{methodName}` (`CartController#addToCart`). 두 형식 혼용 허용 |
| 요청 DTO | record 명시. 없으면 `-` (대시) |
| 응답 DTO | record 명시. void는 `Void`. 페이지네이션은 wrapper DTO (`OrderListResponse` 등) |
| 설명 | 한국어 1줄, 25자 권장 (`dto-doc-standard.md §1줄 요약 길이` 참조) |

### 예시 (api-overview.md line 7, 26 인용)

```
| Cart           | POST | /api/cart/items            | addToCart    | CartItemRequest | CartItemResponse           | 장바구니 아이템 추가     |
| Order Internal | GET  | /internal/orders/{orderId} | getOrderInfo | -               | InternalOrderInfoResponse  | 결제 전 주문 정보 조회 |
```

## 2. 외부 API vs 내부 API 구분

### 분류 기준

| 구분 | 경로 prefix | 호출 주체 |
|---|---|---|
| **외부** | `/api/**` (사용자), `/api/seller/**` (판매자), `/api/admin/**` (관리자) | apigateway 라우팅 통과 |
| **내부** | `/internal/**` | 다른 백엔드 모듈 (REST 동기 호출) |

### 표 분리 규칙

- 같은 모듈이어도 외부/내부 prefix가 다르면 **별도 표**로 분리 (api-overview.md `## 1) External API` / `## 2) Internal API` 패턴).
- 동일 도메인이 외부/내부 양쪽 노출 시 영역명에 "Internal" 접미사 (예: `Order` vs `Order Internal`).

### 예외 (⚠ 확인 필요)

- settlement 모듈 `InternalSettlementController`는 컨트롤러명에 "Internal"이 들어 있으나 실제 path는 `/api/admin/settlements/**` (외부 prefix — `InternalSettlementController.java:21`).
- 이 경우 **path 기준**으로 "외부"로 분류하고 호출 주체(admin)를 비고에 명시.

## 3. 인증 요구 표기

⚠ **확인 필요**: 표에 인증 컬럼 추가 여부 (현재 미적용). 발표 후 회고에서 결정.

### 현재 미적용 사유

- 자동 생성 자산(`api-overview.md`, `api-summary.md`, `api-overview.json`)에 인증 정보 컬럼 없음.
- JWT 검증은 apigateway `JwtAuthenticationFilter`가 일괄 처리. 백엔드 컨트롤러 어노테이션만으로 자동 추출하는 도구 미구현.

### 권장 형식 (발표 후 회고 시)

새 컬럼 후보: `인증`. 후보 값:

| 값 | 의미 |
|---|---|
| `Public` | 인증 불필요 (예: `GET /api/events`, `POST /api/auth/login`) |
| `JWT` | apigateway JWT 검증 통과 필요 |
| `JWT + SELLER` | JWT + SELLER role |
| `JWT + ADMIN` | JWT + ADMIN role |
| `Internal` | `/internal/**` — apigateway `InternalApiBlockFilter`로 외부 차단 |

⚠ 위 형식은 권장안. 실제 적용은 apigateway `RoleAuthorizationFilter` / `RoutePolicy` 룰을 자동 추출 가능한 도구 도입 후 결정.

## 4. 미구현 API 표기

`api-overview.json`의 `notImplemented` 배열 + 컨트롤러 코드 주석 처리(`commented-out`) 항목을 표기.

### 카테고리

| 상태 | 의미 |
|---|---|
| `notImplemented` | 컨트롤러에 시그니처는 있으나 본문 미작성 |
| `commented-out` | 컨트롤러 메서드 자체가 주석 처리됨 (코드는 존재하나 비활성) |

### 표기 형식

```
🔴 {상태} — {사유 1줄}
```

### 예시 (api-overview.json 직접 인용)

```json
"notImplemented": [
  {"path":"/internal/orders/by-event/{eventId}","controller":"InternalOrderController","status":"commented-out"}
]
```

→ 표 표기:

| 영역 | 메서드 | 경로 | Controller | 비고 |
|---|---|---|---|---|
| Order Internal | GET | `/internal/orders/by-event/{eventId}` | `InternalOrderController` | 🔴 commented-out — 환불 fanout 로직이 Kafka로 이행되어 미사용 (api-overview.md line 36-37) |

⚠ 사유는 코드 주석 또는 PR 메시지 인용. 추측 금지.

## 5. 자동 생성 vs 수기 보강 구분

### 자동 생성 영역

- `api-overview.md` / `api-summary.md` / `api-overview.json` — 컨트롤러 어노테이션 스캔 결과
- 자동 추출 신뢰도 ✓: HTTP 메서드, 경로, Controller#method, DTO 시그니처
- ⚠ 자동 생성기 누락 가능 영역: `docs-parser-standard.md` 참조

### 수기 보강 영역 (P5 큐레이션)

- **외부/내부 분리**: prefix 패턴 룰 적용 (자동화 가능)
- **영역명 큐레이션**: 자동은 컨트롤러명 기반 → P5가 도메인명으로 단축 (`CartController` → `Cart`)
- **설명 한국어 1줄**: 자동은 영문 placeholder, P5가 25자 한국어로 큐레이션
- **미구현 사유**: `notImplemented` 항목의 코드 주석 / PR 인용
- **인증 컬럼**: 현재 미적용 (§3 ⚠)
- **모듈 페이지 발췌**: `docs/modules/*.md` §2/§3에 ★ 핵심 플로우 발췌

## 6. 모듈별 API 표 분리 기준

- 모듈마다 별도 섹션 (api-summary.md `## {모듈명}` 패턴)
- 한 모듈 내에서 외부/내부 추가 분리 (api-overview.md commerce 패턴)
- 모듈 페이지(`docs/modules/{모듈}.md`)는 ★ 핵심 플로우 발췌 + api-summary.md / api-overview.md 링크
- DTO 컬럼 표기는 record명만, 상세는 `dto-overview.md` 링크 (§7 참조)

## 7. DTO 인용 형식

- 표의 요청/응답 DTO 컬럼은 record 이름만 (예: `CartItemRequest`)
- 상세 필드는 [dto/dto-overview.md](../dto/dto-overview.md) 또는 [dto/dto-summary.md](../dto/dto-summary.md) 참조
- 모듈 페이지 §5에서 다음 형식으로 링크: `[dto/dto-overview.md](../dto/dto-overview.md) {모듈} 섹션 참조`

## 8. ⚠ 마커 사용 규칙 (API 관련)

`event-schema-standard.md §⚠ 마커 사용 구분`의 패턴 A/B를 API 문서에도 동일 적용 + 패턴 C(비공식) 추가:

| 패턴 | 적용 케이스 | 예시 |
|---|---|---|
| **A** (후행 ⚠) | API는 정상 활성이나 호출자 0건 또는 추가 컨텍스트 필요 | `EventInternalService.deductStock` REST (활성, commerce는 `adjustStockBulk`만 호출) |
| **B** (`⚠ 확인 필요:`) | dead REST / commented-out / notImplemented | commerce `OrderService.completeOrder` (kafka-impl-plan §678 호출자 0건) |
| **C** (드리프트, 비공식) | api-summary.md ↔ controller 코드 불일치, 또는 서비스간 client path 불일치 | admin client `/internal/settlements/run` vs settlement controller `/api/admin/settlements/run` |

⚠ 패턴 C는 `event-schema-standard.md`에 정식 등록 미완 — 발표 후 회고 트랙.

---

⚠ 본 문서는 standards 4종 중 하나. `dto-doc-standard` / `event-schema-standard` / `docs-parser-standard`와 일관성 유지. P5 트랙 산출물 큐레이션 시 4종 병행 적용.
