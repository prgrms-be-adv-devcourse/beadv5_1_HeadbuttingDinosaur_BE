import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getSellerEventDetail, getSellerEventSummary, getSellerEventParticipants } from '../../api/events.api'
import type { SellerEventDetailResponse, SellerEventSummaryResponse, ParticipantItem } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

export default function SellerEventDetail() {
  const { id } = useParams<{ id: string }>()
  const { toast } = useToast()

  const [event, setEvent] = useState<SellerEventDetailResponse | null>(null)
  const [summary, setSummary] = useState<SellerEventSummaryResponse | null>(null)
  const [participants, setParticipants] = useState<ParticipantItem[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')

  useEffect(() => {
    if (!id) return
    Promise.all([
      getSellerEventDetail(id),
      getSellerEventSummary(id),
      getSellerEventParticipants(id, { page: 0, size: 50 }),
    ]).then(([ev, sum, part]) => {
      setEvent(ev.data.data)
      setSummary(sum.data.data)
      setParticipants(part.data.data.content)
    }).catch(() => toast('로드 실패', 'error'))
    .finally(() => setLoading(false))
  }, [id])

  const filtered = participants.filter(p =>
    !keyword || p.nickname.includes(keyword) || p.email.includes(keyword)
  )

  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 100 }}><div className="spinner" /></div>
  if (!event) return null

  const soldRate = summary ? Math.round((summary.soldQuantity / summary.totalQuantity) * 100) : 0

  return (
    <div style={{ padding: '32px 36px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 28 }}>
        <div>
          <div style={{ fontSize: 13, color: 'var(--text-3)', marginBottom: 6 }}>
            <Link to="/seller" style={{ color: 'var(--text-3)' }}>대시보드</Link> › 이벤트 상세
          </div>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>{event.title}</h1>
        </div>
        <Link to={`/seller/events/${id}/edit`} className="btn btn-secondary">수정하기</Link>
      </div>

      {/* Stats */}
      {summary && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 28 }}>
          <div className="stat-card">
            <div className="stat-label">총 수량</div>
            <div className="stat-value">{summary.totalQuantity}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">판매 수량</div>
            <div className="stat-value" style={{ color: 'var(--brand)' }}>{summary.soldQuantity}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">잔여 수량</div>
            <div className="stat-value">{summary.remainingQuantity}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">예상 매출</div>
            <div className="stat-value">{(summary.totalRevenue / 10000).toFixed(1)}만원</div>
          </div>
        </div>
      )}

      {/* Progress bar */}
      {summary && (
        <div className="card" style={{ padding: '20px 24px', marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <span style={{ fontSize: 14, fontWeight: 500 }}>판매율</span>
            <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--brand)' }}>{soldRate}%</span>
          </div>
          <div style={{ height: 10, background: 'var(--surface-2)', borderRadius: 99, overflow: 'hidden' }}>
            <div style={{
              height: '100%', borderRadius: 99,
              background: soldRate >= 80 ? 'var(--danger)' : soldRate >= 50 ? 'var(--warning)' : 'var(--brand)',
              width: `${soldRate}%`, transition: 'width 0.5s ease',
            }} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 12, color: 'var(--text-3)' }}>
            <span>0</span>
            <span>{summary.soldQuantity} / {summary.totalQuantity}장 판매</span>
            <span>{summary.totalQuantity}</span>
          </div>
        </div>
      )}

      {/* Participants */}
      <div className="card" style={{ overflow: 'hidden' }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ fontSize: 15, fontWeight: 600 }}>
            참여자 목록 <span style={{ fontSize: 14, color: 'var(--text-3)', fontWeight: 400 }}>({filtered.length}명)</span>
          </div>
          <div className="search-bar" style={{ width: 220 }}>
            <svg className="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            <input className="search-input" placeholder="이름/이메일 검색"
              value={keyword} onChange={e => setKeyword(e.target.value)}
              style={{ height: 34, fontSize: 13 }} />
          </div>
        </div>

        {filtered.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-3)', fontSize: 14 }}>참여자가 없습니다</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>닉네임</th>
                <th>이메일</th>
                <th>수량</th>
                <th>주문일</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p, i) => (
                <tr key={i}>
                  <td style={{ fontWeight: 500 }}>{p.nickname}</td>
                  <td style={{ color: 'var(--text-3)' }}>{p.email}</td>
                  <td>{p.quantity}장</td>
                  <td style={{ fontSize: 13, color: 'var(--text-3)' }}>
                    {new Date(p.orderedAt).toLocaleDateString('ko-KR')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
