# 이벤트 스키마 정리 기준

> Kafka 이벤트(producer/consumer)를 문서로 정리할 때 따르는 규칙.
> 횡단 설계는 `docs/kafka/kafka-design.md` 인용을 우선한다.

## Kafka Consumer 메서드 표기 규칙

- service-status.md 또는 모듈 페이지에서 Kafka consumer 메서드를 1줄 요약할 때, **수신 토픽명을 반드시 명시한다.**
- 표기 형식: `{토픽명} 수신, {처리 내용}한다`
  - 예: `payment.completed 수신, PAID 전이 + 티켓 발급한다`
  - 예: `stock.deducted 수신, PAYMENT_PENDING 전이한다`
- 토픽명은 `kafka-design.md §3` 정식 표기를 사용한다 (영어 동사 변형 금지).

## ⚠ 마커 사용 구분

- 메서드가 정상 동작하지만 추가 컨텍스트가 필요한 경우:
  `{1줄 요약} ⚠ {추가 컨텍스트}`
  예: `Payment REST 경로로 PAID 전이 + 티켓 발행한다 ⚠ Kafka consumer와 이중 경로`

- 메서드가 stub / deprecated / 비활성인 경우:
  `⚠ 확인 필요: {상세 사유}`
  예: `⚠ 확인 필요: stock.deducted 토픽 비활성(kafka-design §3). 본 메서드는 dedup만 수행하는 stub`
