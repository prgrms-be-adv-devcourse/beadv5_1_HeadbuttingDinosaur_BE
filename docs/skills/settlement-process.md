# 정산프로세스 

## 정산 상태 변화 
- SettlementItem 
  - READY : SettlementItem이 생성되었을때 기본상태값.
  - FINALIZED : 집계되어 Settlement 생성이 완료된경우.
  
- Settlement
  - CONFIRMED : 정산서 생성 완료. 지급 대기 상태.
  - PENDING_MIN_AMOUNT : 정산서 생성완료. 지급 보류(최소 정산금액 1만원 미달, 다음 달 이월)
  - CANCELLED : 생성된 정산서 취소처리
  - PAID : 지급완료
  - PAID_FAILED : 지급실패 (관리자 재시도 대상)

## Settlement 주요 필드 — 문서 개념 vs 엔티티/DB 컬럼

> 본 문서의 `settlementAmount`는 **단일 컬럼이 아닌 계산값**입니다. 코드 검색·SQL 작성 시 컬럼명으로는 잡히지 않으므로 아래 매핑을 참조하세요.

| 문서 개념 | 엔티티 / 컬럼 (`Settlement.java`) | 산출 방식 |
|---|---|---|
| `settlementAmount` (당월 순수 정산금) | **단일 컬럼 없음 — 계산값** | `total_sales_amount` − `total_fee_amount` − `total_refund_amount`. `SettlementAdminServiceImpl.aggregateAmounts(...)` (수동 path) / `MonthlySettlementProcessor.process(...)` (Batch path)에서 산출 |
| `carriedInAmount` (이월 합산액) | `carried_in_amount` : `Integer` | 정산서 생성 시 동일 판매자의 `PENDING_MIN_AMOUNT` 정산서 `final_settlement_amount` 값을 채택 (`SettlementAdminServiceImpl.resolveLatestPending`). 이후 변경 없음 |
| `finalSettlementAmount` (실제 지급금) | `final_settlement_amount` : `Integer` | `(settlementAmount + carriedInAmount)` (`SettlementAdminServiceImpl.processSellerSettlement:331`). 1만원 미달이면 같은 값을 그대로 가지고 `PENDING_MIN_AMOUNT` 상태로 저장되어 다음 달로 이월됨 |
| `carriedToSettlementId` | `carried_to_settlement_id` : `UUID` | 이 정산서 금액이 이월된 대상 정산서 ID (`PENDING_MIN_AMOUNT`인 경우 채워짐) |
| (계산 입력) | `total_sales_amount` : `Integer` | 당월 매출합 |
| (계산 입력) | `total_refund_amount` : `Integer` | 당월 환불합 |
| (계산 입력) | `total_fee_amount` : `Integer` | `total_sales_amount × FeePolicy.feeValue / 100`. `FeePolicy` 엔티티(`fee_policy` 테이블, `feeType=PERCENTAGE`, `feeValue: BigDecimal`)에서 정책 조회. 일별 SettlementItem 생성 시점(`DailySettlementProcessor`)에 적용 |

> **정산서 생성 경로 — 단일 흐름, 트리거 2종**
>
> Settlement 생성 로직은 본질적으로 동일하며, 호출 트리거에 따라 진입점이 두 군데 존재합니다.
>
> | 트리거 | 진입점 | 처리 클래스 | 비고 |
> |--------|--------|-------------|------|
> | **자동(스케줄)** | `SettlementBatchScheduler.launchMonthlySettlementJob` (`@Scheduled(cron="0 10 0 1 * *")`) | Spring Batch `monthlySettlementJob` → `MonthlySettlementProcessor.process(SellerSettlementData)` | 매월 1일 00:10 자동 실행. JobOperator로 Batch Job 시작 |
> | **수동(관리자)** | `SettlementAdminController.runSettlement` (`POST /api/admin/settlements/run`) / `createSettlementFromItems` (`POST /api/admin/settlements/create-from-items`) | `SettlementAdminServiceImpl.createSettlementFromItems` → `processSellerSettlement` (L148, L331) | 운영 수동 트리거. Batch Job을 거치지 않고 Service 메서드로 직접 처리 |
>
> 두 경로 모두 동일 정책 적용: `MIN_SETTLEMENT_AMOUNT = 10_000` 상수, `PENDING_MIN_AMOUNT` 이월, `resolveLatestPending` + `carriedToSettlementId` 갱신.

## 정산대상 데이터 수집 (SettlementItem)
- `SettlementBatchScheduler.launchDailySettlementJob` (`@Scheduled(cron="0 1 0 * * *")`) — 매일 00:01에 `dailySettlementJob` Batch 시작
- 전일 종료된 이벤트의 Ticket 데이터를 기반으로 SettlementItem 생성, 수수료는 `FeePolicy`(PERCENTAGE) 정책에 따라 산정

## 정산서 생성 (Settlement)
- `SettlementBatchScheduler.launchMonthlySettlementJob` (`@Scheduled(cron="0 10 0 1 * *")`) — 매월 1일 00:10에 `monthlySettlementJob` Batch 시작
- 정산대상 :
   - SettlementItem의 status=READY이고 event_date_time(이벤트개최일)이 전전월 26일 ~ 전월 25일에 해당하는 것.
- 이월 처리 (Batch / 수동 path 공통 — 수동 path는 `SettlementAdminServiceImpl.processSellerSettlement`):
   - 동일 판매자의 `PENDING_MIN_AMOUNT` 정산서를 `resolveLatestPending`으로 조회
   - 그 정산서의 `final_settlement_amount`를 당월 정산서의 `carried_in_amount`로 채택
   - 이월된 정산서의 `carried_to_settlement_id` = 당월 정산서 ID로 설정
   - 보조: `includeCarryOverSellers` 가 당월 SettlementItem이 없는 PENDING 판매자도 후보에 포함 (수동 path)
- `finalAmount = settlementAmount + carriedInAmount` 계산 후, **`finalAmount >= 1만원`**(`MIN_SETTLEMENT_AMOUNT` 상수) 이면 `CONFIRMED`, 미만이면 `PENDING_MIN_AMOUNT` 상태로 저장

## 정산서 취소 처리
- 관리자페이지에서 월별 정산서목록보기, 정산서 세부내용 보기 지원
- 정산서 단위로 개별 취소처리
- 취소처리 진행시 상태값 변경
  - Settlement.status = CANCELLED
  - SettlementItem.status = READY
  - 이월 금액이 포함된 경우 : carried_to_settlement_id = 취소된 Settlement.id 인 Settlement들 → PENDING_MIN_AMOUNT로 복구

## 재정산
- 정산서 단위로 재정산 진행 : 해당 월 + 판매자의 정산 재진행

## 지급처리
- Settlement.status = CONFIRMED 인 건을 대상으로 Wallet 서비스에 예치금 전환 요청
- 지급 성공 시
  - Settlement.status = PAID
  - carried_to_settlement_id = 해당 Settlement.id 인 Settlement들(이월된 정산서) → PAID로 변경
- 지급 실패 시
  - Settlement.status = PAID_FAILED
  - 이월된 정산서(carried_to_settlement_id = 해당 Settlement.id)는 상태 유지 (재시도 대기)
- PAID_FAILED 재시도 : 관리자가 Settlement 단위로 재시도. 성공 시 위 지급 성공 흐름 동일하게 처리.
- 이월 정산서 목록 조회 : carried_to_settlement_id 역조회로 확인 (carriedInAmount는 저장된 값 사용)
