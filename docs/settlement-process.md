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
| `settlementAmount` (당월 순수 정산금) | **단일 컬럼 없음 — 계산값** | `total_sales_amount` − `total_fee_amount` − `total_refund_amount` |
| `carriedInAmount` (이월 합산액) | `carried_in_amount` : `Integer` | 정산서 생성 batch 시 동일 판매자의 `PENDING_MIN_AMOUNT` 정산서 `settlementAmount` 합산. 이후 변경 없음 |
| `finalSettlementAmount` (실제 지급금) | `final_settlement_amount` : `Integer` | 개념상 `settlementAmount + carriedInAmount`. 단, 현재 `SettlementItemProcessor.java:35`는 **이월 미반영 식** `totalSalesAmount − feeAmount − totalRefundAmount`로 우선 채움 — 이월 합산 단계 미구현 |
| `carriedToSettlementId` | `carried_to_settlement_id` : `UUID` | 이 정산서 금액이 이월된 대상 정산서 ID (`PENDING_MIN_AMOUNT`인 경우 채워짐) |
| (계산 입력) | `total_sales_amount` : `Integer` | 당월 매출합 |
| (계산 입력) | `total_refund_amount` : `Integer` | 당월 환불합 |
| (계산 입력) | `total_fee_amount` : `Integer` | `total_sales_amount × FEE_RATE`. 현재 `FEE_RATE = 0.05` 상수 (`SettlementItemProcessor.java:19`) — 향후 `feePolicy` 테이블 도입 예정 |

> **의미 차이 — 코드와 문서 (이월 처리 미구현)**
> 본 문서 §"정산서 생성"은 `finalSettlementAmount < 1만원` 시 `PENDING_MIN_AMOUNT` 상태로 **저장 후 이월** 흐름을 규정하나, 현재 `SettlementItemProcessor.java:37-40`은 1만원 미달이면 **`null` 반환으로 Settlement row 자체를 생성하지 않음**. 이월 처리 단계는 미구현이며, 별도 트랙 진행 시 본 매핑을 재정합 필요.

## 정산대상 데이터 수집 (SettlementItem)
- 매일 00:01시간에 SettlementItem 생성 Batch작업
- 전날에 종료된 이벤트를 조회하고 해당 이벤트들의 Ticket데이터를 기반으로 SettlementItem생성

## 정산서 생성 (Settlement)
- 매월 1일 00:10시간에 정산서 생성 Batch작업
- 정산대상 :
   - SettlementItem의 status=READY이고 event_date_time(이벤트개최일)이 전전월 26일 ~ 전월 25일에 해당하는 것.
- 이월 처리 *(설계 — 미구현, 별도 트랙)* :
   - 동일 판매자의 Settlement.status=PENDING_MIN_AMOUNT 건을 조회
   - SUM(settlementAmount)를 당월 정산서의 carriedInAmount로 저장
   - 이월된 각 Settlement.carriedToSettlementId = 당월 정산서 ID로 설정
- finalSettlementAmount(= settlementAmount + carriedInAmount) < 1만원이면 PENDING_MIN_AMOUNT, 이상이면 CONFIRMED *(설계 — 미구현)*

> **현재 코드 동작 (`settlement/.../SettlementItemProcessor.java:34-50`, `SettlementStatus`)**
>
> 1. `feeAmount = totalSalesAmount × 0.05` 계산 (`FEE_RATE` 상수, 향후 `feePolicy` 테이블 도입 예정)
> 2. `finalAmount = totalSalesAmount − feeAmount − totalRefundAmount` 계산 (이월 미반영)
> 3. **`finalAmount < 10000`** 이면 `null` 반환 → **Settlement row 자체가 생성되지 않음** — `PENDING_MIN_AMOUNT` 저장·이월 분기는 미구현
> 4. 1만원 이상이면 `status = CONFIRMED` 로 저장 (이월 합산 로직 미적용 시점이라 `finalSettlementAmount = finalAmount` 상태로 들어감)
>
> 즉, 위 §"정산서 생성"의 **이월 처리 / `PENDING_MIN_AMOUNT` 보류 흐름은 설계만 명시된 미구현 상태**이며, 현 시점에는 1만원 미달 row는 단순 skip 됩니다. 본 흐름이 구현되면 `finalSettlementAmount`가 `settlementAmount + carriedInAmount`로 갱신되고 `SettlementStatus.PENDING_MIN_AMOUNT` 가 실제 사용됩니다.

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
