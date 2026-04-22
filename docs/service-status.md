# 구현된 서비스 현황 (메서드별 1줄 요약)

## admin / AdminSellerService

- `getSellerApplicationList`: get seller application list 기능을 제공.

## admin / AdminSellerServiceImpl

- `decideApplication`: decide application 기능을 제공.
- `getSellerApplicationList`: get seller application list 기능을 제공.

## admin / AdminUserService

- `getMembers`: get members 기능을 제공.

## admin / AdminUserServiceImpl

- `getMembers`: get members 기능을 제공.
- `penalizeUser`: penalize user 기능을 제공.
- `updateUserRole`: update user role 기능을 제공.

## payment / PaymentService

- `readyPayment`: ready payment 기능을 제공.

## payment / PaymentServiceImpl

- `confirmPgPayment`: confirm pg payment 기능을 제공.
- `failPgPayment`: fail pg payment 기능을 제공.
- `getPaymentByOrderId`: get payment by order id 기능을 제공.
- `readyPayment`: ready payment 기능을 제공.

## payment / RefundService

- `getRefundInfo`: get refund info 기능을 제공.

## payment / RefundServiceImpl

- `getRefundDetail`: get refund detail 기능을 제공.
- `getRefundInfo`: get refund info 기능을 제공.
- `getRefundList`: get refund list 기능을 제공.
- `getSellerRefundListByEventId`: get seller refund list by event id 기능을 제공.
- `refundPgTicket`: refund pg ticket 기능을 제공.

## payment / WalletService

- `charge`: charge 기능을 제공.

## payment / WalletServiceImpl

- `charge`: charge 기능을 제공.
- `confirmCharge`: confirm charge 기능을 제공.
- `getBalance`: get balance 기능을 제공.
- `getTransactions`: get transactions 기능을 제공.
- `processBatchRefund`: process batch refund 기능을 제공.
- `processWalletPayment`: process wallet payment 기능을 제공.
- `restoreBalance`: restore balance 기능을 제공.
- `withdraw`: withdraw 기능을 제공.

## settlement / SettlementService

- `fetchSettlementData`: fetch settlement data 기능을 제공.

## settlement / SettlementServiceImpl

- `fetchSettlementData`: fetch settlement data 기능을 제공.
- `getSellerSettlementDetail`: get seller settlement detail 기능을 제공.
- `getSellerSettlements`: get seller settlements 기능을 제공.

