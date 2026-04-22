import { useEffect, useState } from 'react'
import { applyForSeller, getSellerApplicationStatus } from '../api/auth.api'
import type { SellerApplicationStatusResponse } from '../api/types'
import { useToast } from '../contexts/ToastContext'

const STATUS_UI: Record<string, { label: string; color: string; bg: string; desc: string }> = {
  PENDING:  { label: '심사 중',   color: 'var(--warning-text)',  bg: 'var(--warning-bg)',  desc: '관리자가 신청을 검토 중입니다. 영업일 기준 1~3일 내 처리됩니다.' },
  APPROVED: { label: '승인 완료', color: 'var(--success-text)', bg: 'var(--success-bg)', desc: '판매자 권한이 부여되었습니다. 이제 이벤트를 등록할 수 있습니다.' },
  REJECTED: { label: '반려됨',    color: 'var(--danger-text)',  bg: 'var(--danger-bg)',  desc: '신청이 반려되었습니다. 정보를 수정 후 다시 신청해주세요.' },
}

export default function SellerApply() {
  const { toast } = useToast()
  const [appStatus, setAppStatus] = useState<SellerApplicationStatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState({
    businessName: '', businessNumber: '',
    bankName: '', accountNumber: '', accountHolder: '',
  })

  useEffect(() => {
    getSellerApplicationStatus()
      .then(r => setAppStatus(r.data.data))
      .catch(() => {}) // 신청 내역 없으면 null
      .finally(() => setLoading(false))
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.businessName || !form.bankName || !form.accountNumber || !form.accountHolder) {
      toast('모든 항목을 입력해주세요', 'error'); return
    }
    setSubmitting(true)
    try {
      await applyForSeller(form)
      toast('판매자 신청이 완료되었습니다!', 'success')
      const res = await getSellerApplicationStatus()
      setAppStatus(res.data.data)
    } catch { toast('신청 실패. 다시 시도해주세요.', 'error') }
    finally { setSubmitting(false) }
  }

  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 100 }}><div className="spinner" /></div>

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 80, maxWidth: 580 }}>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 6 }}>판매자 전환 신청</h1>
        <p style={{ fontSize: 14, color: 'var(--text-3)' }}>
          이벤트를 직접 등록하고 티켓을 판매할 수 있는 판매자 계정으로 전환을 신청합니다.
        </p>
      </div>

      {/* Status */}
      {appStatus && (
        <div style={{ marginBottom: 24 }}>
          {(() => {
            const ui = STATUS_UI[appStatus.status]
            return (
              <div style={{
                padding: '16px 20px', borderRadius: 'var(--r-lg)',
                background: ui.bg, border: `1px solid ${ui.color}22`,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  <span style={{
                    padding: '2px 8px', borderRadius: 'var(--r-full)',
                    fontSize: 12, fontWeight: 600, background: ui.color, color: '#fff',
                  }}>{ui.label}</span>
                  <span style={{ fontSize: 13, color: 'var(--text-3)' }}>
                    {new Date(appStatus.createdAt).toLocaleDateString('ko-KR')} 신청
                  </span>
                </div>
                <p style={{ fontSize: 14, color: 'var(--text-2)' }}>{ui.desc}</p>
              </div>
            )
          })()}
        </div>
      )}

      {/* Form - PENDING/APPROVED가 아닐 때만 표시 */}
      {(!appStatus || appStatus.status === 'REJECTED') && (
        <div className="card" style={{ padding: '28px' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <Section title="사업자 정보">
              <div className="form-group">
                <label className="form-label">상호명</label>
                <input className="form-input" placeholder="DevConf Lab"
                  value={form.businessName} onChange={e => setForm(f => ({ ...f, businessName: e.target.value }))} />
              </div>
              <div className="form-group">
                <label className="form-label">사업자 등록번호 <span style={{ color: 'var(--text-4)', fontWeight: 400 }}>(선택)</span></label>
                <input className="form-input" placeholder="000-00-00000"
                  value={form.businessNumber} onChange={e => setForm(f => ({ ...f, businessNumber: e.target.value }))} />
              </div>
            </Section>

            <div className="divider" />

            <Section title="정산 계좌 정보">
              <div className="form-group">
                <label className="form-label">은행명</label>
                <input className="form-input" placeholder="카카오뱅크"
                  value={form.bankName} onChange={e => setForm(f => ({ ...f, bankName: e.target.value }))} />
              </div>
              <div className="form-group">
                <label className="form-label">계좌번호</label>
                <input className="form-input" placeholder="- 없이 입력"
                  value={form.accountNumber} onChange={e => setForm(f => ({ ...f, accountNumber: e.target.value }))} />
              </div>
              <div className="form-group">
                <label className="form-label">예금주</label>
                <input className="form-input" placeholder="홍길동"
                  value={form.accountHolder} onChange={e => setForm(f => ({ ...f, accountHolder: e.target.value }))} />
              </div>
            </Section>

            <div style={{ padding: '12px 16px', background: 'var(--surface-2)', borderRadius: 'var(--r-md)', fontSize: 13, color: 'var(--text-3)', lineHeight: 1.7 }}>
              ⚠️ 신청 후 관리자 검토까지 영업일 기준 1~3일이 소요됩니다.<br />
              허위 정보 입력 시 계정이 제한될 수 있습니다.
            </div>

            <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={submitting}>
              {submitting ? '신청 중...' : '판매자 전환 신청'}
            </button>
          </form>
        </div>
      )}

      {appStatus?.status === 'APPROVED' && (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <a href="/seller" className="btn btn-primary btn-lg">판매자 센터로 이동 →</a>
        </div>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-3)', marginBottom: 12, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{title}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>{children}</div>
    </div>
  )
}
