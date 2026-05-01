import { useLocation, useNavigate } from 'react-router-dom'

export default function PaymentComplete() {
  const location = useLocation()
  const navigate = useNavigate()
  const state = location.state as {
    paymentId: string; orderId: string; amount: number; method: string
  } | null

  if (!state) { navigate('/'); return null }

  const methodLabel = state.method === 'WALLET' ? '예치금' : '카드/계좌이체'

  return (
    <div style={{
      minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 440, width: '100%' }}>
        {/* Success icon */}
        <div style={{
          width: 80, height: 80, borderRadius: '50%',
          background: 'var(--success-bg)', color: 'var(--success)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 36, margin: '0 auto 24px',
        }}>✓</div>

        <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 8 }}>결제 완료!</h1>
        <p style={{ fontSize: 15, color: 'var(--text-3)', marginBottom: 32 }}>
          티켓이 발급되었습니다. 마이페이지에서 확인하세요.
        </p>

        {/* Receipt card */}
        <div className="card" style={{ padding: '20px 24px', textAlign: 'left', marginBottom: 24 }}>
          <Row label="결제 금액" value={`${state.amount.toLocaleString()}원`} bold />
          <Row label="결제 수단" value={methodLabel} />
          <Row label="주문번호" value={
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
              {state.orderId.slice(0, 20)}...
            </span>
          } />
          <Row label="결제 일시" value={new Date().toLocaleString('ko-KR')} />
        </div>

        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-secondary" style={{ flex: 1, justifyContent: 'center' }}
            onClick={() => navigate('/mypage?tab=orders')}>주문 상세</button>
          <button className="btn btn-primary" style={{ flex: 1, justifyContent: 'center' }}
            onClick={() => navigate('/mypage?tab=tickets')}>내 티켓 보기</button>
        </div>
      </div>
    </div>
  )
}

function Row({ label, value, bold }: { label: string; value: React.ReactNode; bold?: boolean }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '10px 0', borderBottom: '1px solid var(--border)',
    }}>
      <span style={{ fontSize: 13, color: 'var(--text-3)' }}>{label}</span>
      <span style={{ fontSize: bold ? 16 : 14, fontWeight: bold ? 700 : 500, color: 'var(--text)' }}>
        {value}
      </span>
    </div>
  )
}
