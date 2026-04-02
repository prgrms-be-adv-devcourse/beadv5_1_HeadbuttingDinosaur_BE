import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { readyPayment, confirmPayment } from '../api/payments.api'
import { getWalletBalance } from '../api/wallet.api'
import { useEffect } from 'react'
import { useToast } from '../contexts/ToastContext'

export default function Payment() {
  const location = useLocation()
  const navigate = useNavigate()
  const { toast } = useToast()
  const state = location.state as { orderId: string; totalAmount: number } | null

  const [method, setMethod] = useState<'WALLET' | 'PG'>('PG')
  const [walletBalance, setWalletBalance] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getWalletBalance().then(res => {
      setWalletBalance(res.data.data.balance)
    }).catch(() => {})
  }, [])

  if (!state) { navigate('/'); return null }

  const handlePay = async () => {
    setLoading(true)
    try {
      const res = await readyPayment({ orderId: state.orderId, paymentMethod: method })
      const payment = res.data

      if (method === 'WALLET') {
        // 예치금 결제는 바로 confirm
        await confirmPayment({
          paymentId: payment.paymentId,
          paymentKey: 'WALLET',
          orderId: state.orderId,
          amount: state.totalAmount,
        })
        navigate('/payment/complete', { state: { paymentId: payment.paymentId, orderId: state.orderId, amount: state.totalAmount, method: 'WALLET' } })
      } else {
        // Toss PG → 실제 환경에서는 tossPaymentUrl로 이동
        // 여기서는 시뮬레이션
        if (payment.tossPaymentUrl) {
          window.location.href = payment.tossPaymentUrl
        } else {
          // Demo: 바로 complete
          navigate('/payment/complete', { state: { paymentId: payment.paymentId, orderId: state.orderId, amount: state.totalAmount, method: 'PG' } })
        }
      }
    } catch {
      toast('결제 처리 중 오류가 발생했습니다', 'error')
    } finally {
      setLoading(false)
    }
  }

  const walletInsufficient = method === 'WALLET' && walletBalance !== null && walletBalance < state.totalAmount

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 80, maxWidth: 680 }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 28 }}>결제</h1>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* Order summary */}
        <div className="card" style={{ padding: '20px' }}>
          <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 14 }}>주문 정보</h2>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: 'var(--text-2)', marginBottom: 8 }}>
            <span>주문번호</span>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{state.orderId.slice(0, 16)}...</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 16, fontWeight: 700, color: 'var(--text)', paddingTop: 10, borderTop: '1px solid var(--border)' }}>
            <span>결제 금액</span>
            <span>{state.totalAmount.toLocaleString()}원</span>
          </div>
        </div>

        {/* Payment method */}
        <div className="card" style={{ padding: '20px' }}>
          <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 14 }}>결제 수단</h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {/* Toss */}
            <label style={{
              display: 'flex', alignItems: 'center', gap: 12,
              padding: '14px 16px', borderRadius: 'var(--r-md)', cursor: 'pointer',
              border: `1.5px solid ${method === 'PG' ? 'var(--brand)' : 'var(--border)'}`,
              background: method === 'PG' ? 'var(--brand-light)' : 'var(--surface)',
              transition: 'all 0.15s',
            }}>
              <input type="radio" checked={method === 'PG'} onChange={() => setMethod('PG')} style={{ display: 'none' }} />
              <div style={{
                width: 20, height: 20, borderRadius: '50%',
                border: `2px solid ${method === 'PG' ? 'var(--brand)' : 'var(--border-2)'}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                {method === 'PG' && <div style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--brand)' }} />}
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 500 }}>카드 / 계좌이체 (Toss Payments)</div>
                <div style={{ fontSize: 12, color: 'var(--text-3)' }}>신용카드, 체크카드, 실시간 계좌이체</div>
              </div>
            </label>

            {/* Wallet */}
            <label style={{
              display: 'flex', alignItems: 'center', gap: 12,
              padding: '14px 16px', borderRadius: 'var(--r-md)', cursor: 'pointer',
              border: `1.5px solid ${method === 'WALLET' ? 'var(--brand)' : 'var(--border)'}`,
              background: method === 'WALLET' ? 'var(--brand-light)' : 'var(--surface)',
              transition: 'all 0.15s',
            }}>
              <input type="radio" checked={method === 'WALLET'} onChange={() => setMethod('WALLET')} style={{ display: 'none' }} />
              <div style={{
                width: 20, height: 20, borderRadius: '50%',
                border: `2px solid ${method === 'WALLET' ? 'var(--brand)' : 'var(--border-2)'}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                {method === 'WALLET' && <div style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--brand)' }} />}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 500 }}>예치금 결제</div>
                <div style={{ fontSize: 12, color: walletInsufficient ? 'var(--danger)' : 'var(--text-3)' }}>
                  보유 예치금:{' '}
                  {walletBalance === null ? '확인 중...' : `${walletBalance.toLocaleString()}원`}
                  {walletInsufficient && ' (잔액 부족)'}
                </div>
              </div>
            </label>
          </div>
        </div>

        {/* Pay button */}
        <button
          className="btn btn-primary btn-full btn-lg"
          onClick={handlePay}
          disabled={loading || walletInsufficient}
        >
          {loading ? '처리 중...' : `${state.totalAmount.toLocaleString()}원 결제하기`}
        </button>
      </div>
    </div>
  )
}
