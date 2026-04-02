import { useEffect, useState } from 'react'
import { getSellerSettlements } from '../../api/seller.api'
import type { SettlementItem } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

const STATUS_MAP: Record<string, { label: string; cls: string }> = {
  PENDING:   { label: '정산 대기', cls: 'badge-amber' },
  COMPLETED: { label: '정산 완료', cls: 'badge-green' },
  CANCELLED: { label: '취소됨',   cls: 'badge-gray' },
}

export default function SellerSettlement() {
  const { toast } = useToast()
  const [settlements, setSettlements] = useState<SettlementItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getSellerSettlements()
      .then(r => setSettlements(r.data.data.content))
      .catch(() => toast('로드 실패', 'error'))
      .finally(() => setLoading(false))
  }, [])

  const totalNet = settlements.reduce((acc, s) => acc + s.netAmount, 0)
  const totalFee = settlements.reduce((acc, s) => acc + s.feeAmount, 0)
  const totalGross = settlements.reduce((acc, s) => acc + s.settledAmount, 0)

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>정산 내역</h1>
        <p style={{ fontSize: 14, color: 'var(--text-3)' }}>이벤트별 정산 현황을 확인하세요</p>
      </div>

      {/* Summary */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 28 }}>
        <div className="stat-card">
          <div className="stat-label">총 판매 금액</div>
          <div className="stat-value">{(totalGross / 10000).toFixed(1)}만원</div>
          <div className="stat-sub">수수료 차감 전</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">플랫폼 수수료</div>
          <div className="stat-value" style={{ color: 'var(--danger)' }}>−{(totalFee / 10000).toFixed(1)}만원</div>
          <div className="stat-sub">부가세 포함</div>
        </div>
        <div className="stat-card" style={{ borderColor: 'var(--brand)', borderWidth: 2 }}>
          <div className="stat-label">최종 정산액</div>
          <div className="stat-value" style={{ color: 'var(--brand)' }}>{(totalNet / 10000).toFixed(1)}만원</div>
          <div className="stat-sub">입금 예정</div>
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : settlements.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">💰</div>
          <div className="empty-title">정산 내역이 없습니다</div>
          <div className="empty-desc">이벤트 판매 후 정산이 완료되면 여기에 표시됩니다</div>
        </div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>이벤트</th>
                <th style={{ textAlign: 'right' }}>판매 금액</th>
                <th style={{ textAlign: 'right' }}>수수료</th>
                <th style={{ textAlign: 'right' }}>정산액</th>
                <th>상태</th>
                <th>정산일</th>
              </tr>
            </thead>
            <tbody>
              {settlements.map(s => {
                const status = STATUS_MAP[s.status] ?? { label: s.status, cls: 'badge-gray' }
                return (
                  <tr key={s.settlementId}>
                    <td>
                      <div style={{ fontWeight: 500 }}>{s.eventTitle}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-4)', fontFamily: 'var(--font-mono)' }}>
                        #{s.eventId.slice(0, 10)}
                      </div>
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 500 }}>{s.settledAmount.toLocaleString()}원</td>
                    <td style={{ textAlign: 'right', color: 'var(--danger)' }}>−{s.feeAmount.toLocaleString()}원</td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--brand)' }}>{s.netAmount.toLocaleString()}원</td>
                    <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                    <td style={{ fontSize: 13, color: 'var(--text-3)' }}>
                      {s.settledAt ? new Date(s.settledAt).toLocaleDateString('ko-KR') : '—'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
