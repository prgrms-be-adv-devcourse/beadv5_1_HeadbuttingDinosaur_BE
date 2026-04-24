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

## Settlement 주요 필드
- settlementAmount : 당월 순수 정산금 (당월 SettlementItem 기반)
- carriedInAmount : 이월받은 금액 합산 (정산서 생성 batch 시 계산 후 저장, 이후 변경 없음)
- finalSettlementAmount : 실제 지급금 (= settlementAmount + carriedInAmount)
- carriedToSettlementId : 이 정산서의 금액이 이월된 대상 정산서 ID (PENDING_MIN_AMOUNT인 경우 채워짐)

## 정산대상 데이터 수집 (SettlementItem)
- 매일 00:01시간에 SettlementItem 생성 Batch작업
- 전날에 종료된 이벤트를 조회하고 해당 이벤트들의 Ticket데이터를 기반으로 SettlementItem생성

## 정산서 생성 (Settlement)
- 매월 1일 00:10시간에 정산서 생성 Batch작업
- 정산대상 :
   - SettlementItem의 status=READY이고 event_date_time(이벤트개최일)이 전전월 26일 ~ 전월 25일에 해당하는 것.
- 이월 처리 :
   - 동일 판매자의 Settlement.status=PENDING_MIN_AMOUNT 건을 조회
   - SUM(settlementAmount)를 당월 정산서의 carriedInAmount로 저장
   - 이월된 각 Settlement.carriedToSettlementId = 당월 정산서 ID로 설정
- finalSettlementAmount(= settlementAmount + carriedInAmount) < 1만원이면 PENDING_MIN_AMOUNT, 이상이면 CONFIRMED

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
