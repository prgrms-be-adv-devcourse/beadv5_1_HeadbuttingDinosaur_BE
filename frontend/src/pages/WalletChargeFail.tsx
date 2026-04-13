import { useSearchParams, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'

export default function WalletChargeFail() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const errorCode    = searchParams.get('code') ?? ''
  const errorMessage = searchParams.get('message') ?? '충전이 취소되었거나 실패했습니다.'

  useEffect(() => {
    sessionStorage.removeItem('wallet_charge_context')
  }, [])

  return (
    <div style={{
      minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 400 }}>
        <div style={{
          width: 64, height: 64, borderRadius: '50%',
          background: 'var(--danger-bg)', color: 'var(--danger)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 28, margin: '0 auto 20px',
        }}>✕</div>
        <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>충전 실패</h2>
        <p style={{ fontSize: 14, color: 'var(--text-3)', marginBottom: 4 }}>{errorMessage}</p>
        {errorCode && (
          <p style={{ fontSize: 12, color: 'var(--text-4)', fontFamily: 'var(--font-mono)', marginBottom: 24 }}>
            오류 코드: {errorCode}
          </p>
        )}
        <button className="btn btn-primary" onClick={() => navigate('/mypage?tab=wallet', { replace: true })}>
          예치금 페이지로 돌아가기
        </button>
      </div>
    </div>
  )
}
