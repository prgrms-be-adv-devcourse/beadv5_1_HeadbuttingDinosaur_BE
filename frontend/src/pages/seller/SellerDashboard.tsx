import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSellerEvents, stopSellerEvent } from '../../api/events.api'
import type { SellerEventItem } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

const STATUS_MAP: Record<string, { label: string; cls: string }> = {
  DRAFT:          { label: '초안',    cls: 'badge-gray' },
  ON_SALE:        { label: '판매중',  cls: 'badge-green' },
  SOLD_OUT:       { label: '매진',    cls: 'badge-red' },
  SALE_ENDED:     { label: '종료',    cls: 'badge-gray' },
  CANCELLED:      { label: '취소됨',  cls: 'badge-gray' },
}

const STATUS_TABS = ['전체', 'ON_SALE', 'SALE_ENDED', 'CANCELLED']
const TAB_LABELS: Record<string, string> = { '전체': '전체', ON_SALE: '판매중', SALE_ENDED: '종료', CANCELLED: '취소됨' }

export default function SellerDashboard() {
  const { toast } = useToast()
  const [events, setEvents] = useState<SellerEventItem[]>([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('전체')

  const fetchEvents = async () => {
    setLoading(true)
    try {
      const res = await getSellerEvents({ status: activeTab === '전체' ? undefined : activeTab, page: 0, size: 50 })
      setEvents(res.data.data.content)
    } catch { toast('이벤트 로드 실패', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchEvents() }, [activeTab])

  const handleStop = async (eventId: string, title: string) => {
    if (!confirm(`"${title}" 판매를 중지할까요?`)) return
    try {
      await stopSellerEvent(eventId)
      toast('판매 중지되었습니다', 'success')
      fetchEvents()
    } catch { toast('처리 실패', 'error') }
  }

  // Quick stats
  const totalRevenue = events.filter(e => e.status === 'ON_SALE' || e.status === 'ENDED')
    .reduce((acc, e) => acc + (e.totalQuantity - e.remainingQuantity) * e.price, 0)
  const onSaleCount = events.filter(e => e.status === 'ON_SALE').length
  const totalSold = events.reduce((acc, e) => acc + (e.totalQuantity - e.remainingQuantity), 0)

  return (
    <div style={{ padding: '32px 36px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>판매자 대시보드</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)' }}>이벤트를 관리하고 판매 현황을 확인하세요</p>
        </div>
        <Link to="/seller/events/create" className="btn btn-primary">
          + 이벤트 등록
        </Link>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 28 }}>
        <div className="stat-card">
          <div className="stat-label">판매중 이벤트</div>
          <div className="stat-value">{onSaleCount}</div>
          <div className="stat-sub">현재 활성</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">총 판매 티켓</div>
          <div className="stat-value">{totalSold.toLocaleString()}</div>
          <div className="stat-sub">전체 이벤트 합산</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">예상 매출</div>
          <div className="stat-value">{totalRevenue >= 10000 ? `${Math.floor(totalRevenue / 10000)}만` : totalRevenue.toLocaleString()}원</div>
          <div className="stat-sub">수수료 차감 전</div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, marginBottom: 16, borderBottom: '1px solid var(--border)', paddingBottom: 0 }}>
        {STATUS_TABS.map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)} style={{
            padding: '8px 14px', fontSize: 13, fontWeight: activeTab === tab ? 600 : 400,
            color: activeTab === tab ? 'var(--brand)' : 'var(--text-3)',
            background: 'none', border: 'none',
            borderBottom: `2px solid ${activeTab === tab ? 'var(--brand)' : 'transparent'}`,
            cursor: 'pointer', marginBottom: -1, transition: 'all 0.15s',
          }}>{TAB_LABELS[tab]}</button>
        ))}
      </div>

      {/* Events table */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : events.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🎫</div>
          <div className="empty-title">등록된 이벤트가 없습니다</div>
          <Link to="/seller/events/create" className="btn btn-primary" style={{ marginTop: 8 }}>첫 이벤트 등록하기</Link>
        </div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>이벤트명</th>
                <th>상태</th>
                <th>일시</th>
                <th>가격</th>
                <th style={{ textAlign: 'right' }}>판매/잔여</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {events.map(event => {
                const status = STATUS_MAP[event.status] ?? { label: event.status, cls: 'badge-gray' }
                const sold = event.totalQuantity - event.remainingQuantity
                return (
                  <tr key={event.eventId}>
                    <td>
                      <div style={{ fontWeight: 500, marginBottom: 2 }}>{event.title}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-4)', fontFamily: 'var(--font-mono)' }}>
                        #{event.eventId.slice(0, 10)}
                      </div>
                    </td>
                    <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                    <td style={{ fontSize: 13, color: 'var(--text-3)', whiteSpace: 'nowrap' }}>
                      {new Date(event.eventDateTime).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}
                    </td>
                    <td style={{ fontWeight: 500 }}>
                      {event.price === 0 ? <span style={{ color: 'var(--success)' }}>무료</span> : `${event.price.toLocaleString()}원`}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <span style={{ fontWeight: 600 }}>{sold}</span>
                      <span style={{ color: 'var(--text-3)' }}> / {event.remainingQuantity}</span>
                      <div style={{ height: 4, background: 'var(--surface-2)', borderRadius: 99, marginTop: 4, minWidth: 60 }}>
                        <div style={{
                          height: '100%', borderRadius: 99,
                          background: 'var(--brand)',
                          width: `${Math.min(100, (sold / event.totalQuantity) * 100)}%`,
                        }} />
                      </div>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <Link to={`/seller/events/${event.eventId}`} className="btn btn-ghost btn-sm">상세</Link>
                        <Link to={`/seller/events/${event.eventId}/edit`} className="btn btn-secondary btn-sm">수정</Link>
                        {event.status === 'ON_SALE' && (
                          <button className="btn btn-danger btn-sm" onClick={() => handleStop(event.eventId, event.title)}>중지</button>
                        )}
                      </div>
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
