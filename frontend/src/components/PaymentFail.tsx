import { useSearchParams, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'

export default function PaymentFail() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const code = searchParams.get('code') || '알 수 없는 오류'
  const message = searchParams.get('message') || '결제 처리 중 문제가 발생했습니다.'

  useEffect(() => {
    sessionStorage.removeItem('payment_context')
  }, [])

  return (
    <div style={{
      minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 400 }}>
        <div style={{
          width: 64, height: 64, borderRadius: '50%',
          background: 'var(--danger-bg)', color: 'var(--danger)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 28, margin: '0 auto 20px',
        }}>✕</div>

        <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>결제 실패</h2>
        <p style={{ fontSize: 14, color: 'var(--text-3)', marginBottom: 6 }}>{message}</p>
        <p style={{ fontSize: 12, color: 'var(--text-4)', marginBottom: 28 }}>오류 코드: {code}</p>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>
            뒤로가기
          </button>
          <button className="btn btn-primary" onClick={() => navigate('/', { replace: true })}>
            홈으로
          </button>
        </div>
      </div>
    </div>
  )
}