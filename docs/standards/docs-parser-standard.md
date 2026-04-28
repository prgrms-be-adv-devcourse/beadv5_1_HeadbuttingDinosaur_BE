# docs parser 정리 기준

> 자동 생성 자산(`api-overview`, `dto-overview`, `service-status` 등)의 파서/생성기 동작 규칙.

## 스캔 대상 디렉토리 패턴 (⚠ 미해결)

현재 자동 생성기가 `**/application/*Service*.java` 직속 패턴을 누락하고 있음.
event/member 모듈이 service-status.md에 빠진 원인.

해결 시점: 발표 후 회고 단계. 발표 전엔 ServiceOverview에 수동 보강으로 우회.
⚠ 확인 필요: 자동 생성 도구 책임자 (P2 또는 미정) 확인 필요.
