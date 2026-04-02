import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { confirmPayment } from '../api/payments.api'

export default function PaymentSuccess() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    const paymentKey = searchParams.get('paymentKey')
    const tossOrderId = searchParams.get('orderId')  // 우리 paymentId
    const amount = searchParams.get('amount')

    // sessionStorage에서 결제 컨텍스트 복구
    const ctx = sessionStorage.getItem('payment_context')
    const paymentContext = ctx ? JSON.parse(ctx) : null

    if (!paymentKey || !tossOrderId || !amount || !paymentContext) {
      setStatus('error')
      setErrorMsg('결제 정보가 유효하지 않습니다.')
      return
    }

    const doConfirm = async () => {
      try {
        await confirmPayment({
          paymentId: paymentContext.paymentId,
          paymentKey,
          orderId: paymentContext.orderId,
          amount: Number(amount),
        })

        sessionStorage.removeItem('payment_context')
        setStatus('success')

        // 2초 후 결제 완료 페이지로 이동
        setTimeout(() => {
          navigate('/payment/complete', {
            state: {
              paymentId: paymentContext.paymentId,
              orderId: paymentContext.orderId,
              amount: Number(amount),
              method: 'PG',
            },
            replace: true,
          })
        }, 1500)
      } catch (e: any) {
        setStatus('error')
        setErrorMsg(e?.response?.data?.message || '결제 승인 처리에 실패했습니다.')
        sessionStorage.removeItem('payment_context')
      }
    }

    doConfirm()
  }, [searchParams, navigate])

  return (
    <div style={{
      minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 400 }}>
        {status === 'loading' && (
          <>
            <div className="spinner" style={{ margin: '0 auto 24px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>결제 승인 처리 중...</h2>
            <p style={{ fontSize: 14, color: 'var(--text-3)' }}>잠시만 기다려주세요.</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: 'var(--success-bg)', color: 'var(--success)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 28, margin: '0 auto 20px',
            }}>✓</div>
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>결제 승인 완료!</h2>
            <p style={{ fontSize: 14, color: 'var(--text-3)' }}>잠시 후 결제 완료 페이지로 이동합니다.</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: 'var(--danger-bg)', color: 'var(--danger)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 28, margin: '0 auto 20px',
            }}>✕</div>
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>결제 승인 실패</h2>
            <p style={{ fontSize: 14, color: 'var(--text-3)', marginBottom: 24 }}>{errorMsg}</p>
            <button className="btn btn-primary" onClick={() => navigate('/', { replace: true })}>
              홈으로 돌아가기
            </button>
          </>
        )}
      </div>
    </div>
  )
}