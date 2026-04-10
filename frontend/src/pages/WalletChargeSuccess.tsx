import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { confirmWalletCharge } from '../api/wallet.api'

export default function WalletChargeSuccess() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    const paymentKey = searchParams.get('paymentKey')
    const orderId    = searchParams.get('orderId')   // Toss가 돌려준 값 = 우리가 보낸 chargeId
    const amount     = searchParams.get('amount')

    if (!paymentKey || !orderId || !amount) {
      setStatus('error')
      setErrorMsg('결제 정보가 유효하지 않습니다.')
      return
    }

    const doConfirm = async () => {
      try {
        await confirmWalletCharge({
          chargeId: orderId,   // orderId(Toss) → chargeId(백엔드) 매핑
          paymentKey,
          amount: Number(amount),
        })
        sessionStorage.removeItem('wallet_charge_context')
        setStatus('success')
        setTimeout(() => navigate('/mypage?tab=wallet', { replace: true }), 1500)
      } catch (e: any) {
        setStatus('error')
        const msg = e?.response?.data?.message ?? e?.message ?? '충전 승인 처리에 실패했습니다.'
        setErrorMsg(msg)
        sessionStorage.removeItem('wallet_charge_context')
      }
    }

    doConfirm()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div style={{
      minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 400 }}>
        {status === 'loading' && (
          <>
            <div className="spinner" style={{ margin: '0 auto 24px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>충전 승인 처리 중...</h2>
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
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>충전 완료!</h2>
            <p style={{ fontSize: 14, color: 'var(--text-3)' }}>잠시 후 예치금 페이지로 이동합니다.</p>
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
            <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 8 }}>충전 실패</h2>
            <p style={{ fontSize: 14, color: 'var(--text-3)', marginBottom: 24 }}>{errorMsg}</p>
            <button className="btn btn-primary" onClick={() => navigate('/mypage?tab=wallet', { replace: true })}>
              예치금 페이지로 돌아가기
            </button>
          </>
        )}
      </div>
    </div>
  )
}
