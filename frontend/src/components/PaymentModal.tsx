import { useEffect, useState } from 'react'
import { readyPayment, confirmPayment } from '../api/payments.api'
import { getWalletBalance } from '../api/wallet.api'
import { useToast } from '../contexts/ToastContext'
import { useAuth } from '../contexts/AuthContext'

declare global {
  interface Window {
    TossPayments: (clientKey: string) => any
  }
}

const TOSS_CLIENT_KEY = 'test_ck_GjLJoQ1aVZplbR1KB0MW8w6KYe2R'

interface Props {
  open: boolean
  orderId: string
  totalAmount: number
  onClose: () => void
  onSuccess: () => void
}

type Method = 'PG' | 'WALLET'

export default function PaymentModal({ open, orderId, totalAmount, onClose, onSuccess }: Props) {
  const { toast } = useToast()
  const { user } = useAuth()
  const [method, setMethod] = useState<Method>('PG')
  const [walletBalance, setWalletBalance] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)

  // 모달 열릴 때 예치금 잔액 조회
  useEffect(() => {
    if (!open) return
    getWalletBalance()
      .then(r => setWalletBalance(r.data.balance))
      .catch(() => setWalletBalance(null))
  }, [open])

  // ESC 닫기
  useEffect(() => {
    if (!open) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [open, onClose])

  if (!open) return null

  const walletInsufficient = method === 'WALLET' && walletBalance !== null && walletBalance < totalAmount

  const handlePay = async () => {
    setLoading(true)
    try {
      // 1) 결제 준비
      const readyRes = await readyPayment({ orderId, paymentMethod: method })
      const payment = readyRes.data

      if (method === 'WALLET') {
        // 2-A) 예치금: 바로 confirm
        await confirmPayment({
          paymentId: payment.paymentId,
          paymentKey: 'WALLET',
          orderId,
          amount: totalAmount,
        })
        toast('결제가 완료되었습니다!', 'success')
        onSuccess()
      } else {
        // 2-B) Toss PG 결제창 호출
        // 성공 시 필요한 정보를 sessionStorage에 저장
        sessionStorage.setItem('payment_context', JSON.stringify({
          paymentId: payment.paymentId,
          orderId,
          totalAmount,
        }))

        const customerKey = user?.userId || `guest_${Date.now()}`
        const tossPayments = window.TossPayments(TOSS_CLIENT_KEY)
        const tossPayment = tossPayments.payment({ customerKey })

        await tossPayment.requestPayment({
          method: 'CARD',
          amount: { currency: 'KRW', value: totalAmount },
          orderId: payment.paymentId,  // Toss orderId = 우리 paymentId
          orderName: '이벤트 티켓',
          successUrl: `${window.location.origin}/payment/success`,
          failUrl: `${window.location.origin}/payment/fail`,
        })
      }
    } catch (e: any) {
      // Toss SDK 취소 시에도 여기로 옴 (사용자가 결제창 닫은 경우)
      if (e?.code === 'USER_CANCEL' || e?.code === 'PAY_PROCESS_CANCELED') {
        toast('결제가 취소되었습니다.', 'info')
      } else {
        toast('결제 처리 중 오류가 발생했습니다.', 'error')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
      style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        background: 'rgba(0,0,0,0.5)',
        backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 24,
        animation: 'fadeIn 0.15s ease',
      }}
    >
      <div style={{
        width: '100%', maxWidth: 460,
        background: 'var(--surface)',
        borderRadius: 'var(--r-xl)',
        boxShadow: '0 24px 64px rgba(0,0,0,0.2)',
        overflow: 'hidden',
        animation: 'slideUp 0.2s ease',
      }}>
        {/* Header */}
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '20px 24px',
          borderBottom: '1px solid var(--border)',
        }}>
          <h2 style={{ fontSize: 17, fontWeight: 700, color: 'var(--text)' }}>결제</h2>
          <button onClick={onClose} style={{
            width: 30, height: 30, borderRadius: 'var(--r-md)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--text-4)', fontSize: 18, transition: 'background 0.1s',
          }}
          onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-2)')}
          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          >✕</button>
        </div>

        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 주문 요약 */}
          <div style={{
            padding: '14px 16px',
            background: 'var(--surface-2)',
            borderRadius: 'var(--r-md)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          }}>
            <span style={{ fontSize: 14, color: 'var(--text-2)' }}>결제 금액</span>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'var(--text)' }}>
              {totalAmount.toLocaleString()}원
            </span>
          </div>

          {/* 결제 수단 선택 */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-3)', marginBottom: 10 }}>
              결제 수단
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <MethodCard
                selected={method === 'PG'}
                onClick={() => setMethod('PG')}
                icon={<TossIcon />}
                title="카드 / 계좌이체"
                desc="신용카드, 체크카드, 실시간 계좌이체"
              />
              <MethodCard
                selected={method === 'WALLET'}
                onClick={() => setMethod('WALLET')}
                icon={<span style={{ fontSize: 18 }}>💰</span>}
                title="예치금 결제"
                desc={
                  walletBalance === null
                    ? '잔액 확인 중...'
                    : walletInsufficient
                    ? `잔액 ${walletBalance.toLocaleString()}원 · 부족`
                    : `잔액 ${walletBalance.toLocaleString()}원`
                }
                warn={walletInsufficient}
              />
            </div>
          </div>

          {walletInsufficient && (
            <div style={{
              padding: '10px 14px',
              background: 'var(--danger-bg)',
              borderRadius: 'var(--r-md)',
              fontSize: 13,
              color: 'var(--danger-text)',
              display: 'flex', alignItems: 'center', gap: 6,
            }}>
              ⚠ 예치금 잔액이 부족합니다. 카드 결제를 이용하거나 예치금을 충전해주세요.
            </div>
          )}
        </div>

        {/* Footer */}
        <div style={{
          padding: '16px 24px',
          borderTop: '1px solid var(--border)',
          display: 'flex', gap: 10,
          background: 'var(--surface-2)',
        }}>
          <button
            className="btn btn-secondary"
            style={{ flex: 1, justifyContent: 'center' }}
            onClick={onClose}
            disabled={loading}
          >취소</button>
          <button
            className="btn btn-primary"
            style={{ flex: 2, justifyContent: 'center', fontSize: 15, fontWeight: 700 }}
            onClick={handlePay}
            disabled={loading || walletInsufficient}
          >
            {loading
              ? <><div className="spinner" style={{ width: 16, height: 16, borderTopColor: '#fff' }} />처리 중...</>
              : `${totalAmount.toLocaleString()}원 결제`}
          </button>
        </div>
      </div>

      <style>{`
        @keyframes fadeIn  { from { opacity: 0 } to { opacity: 1 } }
        @keyframes slideUp { from { transform: translateY(20px); opacity: 0 } to { transform: none; opacity: 1 } }
      `}</style>
    </div>
  )
}

// ── 결제 수단 카드 ────────────────────────────────────────────────
interface MethodCardProps {
  selected: boolean
  onClick: () => void
  icon: React.ReactNode
  title: string
  desc: string
  warn?: boolean
}

function MethodCard({ selected, onClick, icon, title, desc, warn }: MethodCardProps) {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'flex', alignItems: 'center', gap: 14,
        padding: '14px 16px',
        borderRadius: 'var(--r-md)',
        border: `1.5px solid ${selected ? 'var(--brand)' : 'var(--border)'}`,
        background: selected ? 'var(--brand-light)' : 'var(--surface)',
        cursor: 'pointer',
        textAlign: 'left',
        width: '100%',
        transition: 'all 0.15s',
      }}
    >
      <div style={{
        width: 18, height: 18, borderRadius: '50%', flexShrink: 0,
        border: `2px solid ${selected ? 'var(--brand)' : 'var(--border-2)'}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'border-color 0.15s',
      }}>
        {selected && (
          <div style={{ width: 9, height: 9, borderRadius: '50%', background: 'var(--brand)' }} />
        )}
      </div>
      <div style={{
        width: 36, height: 36, borderRadius: 'var(--r-sm)',
        background: 'var(--surface-2)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        flexShrink: 0,
      }}>{icon}</div>
      <div>
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text)', marginBottom: 2 }}>{title}</div>
        <div style={{ fontSize: 12, color: warn ? 'var(--danger)' : 'var(--text-3)' }}>{desc}</div>
      </div>
    </button>
  )
}

function TossIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <rect width="24" height="24" rx="6" fill="#0064FF"/>
      <path d="M7 9h10M7 12h7M7 15h5" stroke="white" strokeWidth="1.8" strokeLinecap="round"/>
    </svg>
  )
}