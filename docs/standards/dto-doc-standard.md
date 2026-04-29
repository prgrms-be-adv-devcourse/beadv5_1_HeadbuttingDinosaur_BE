# DTO 문서화 기준

> `docs/dto/` 자동 생성 자산을 큐레이션할 때 따르는 규칙.

## Service 매핑 규칙

- Service 인터페이스가 있는 경우, **인터페이스 기준**으로 매핑한다.
- `*Impl` 전용 메서드(인터페이스에 없는 메서드)는 별도 분류로 표기한다.
  - 표기 형식: `{ClassName}Impl 전용 — {용도}` (예: "WalletServiceImpl 전용 — recovery helper")
- 인터페이스가 없는 Service는 클래스 자체를 기준으로 한다.

## 1줄 요약 길이

- 1줄 요약은 25자 이내 권장. 단, Kafka consumer 등 핵심 책임이 3개 이상인 메서드는 35자까지 허용.
