// ── AdminEvents ────────────────────────────────────────────────────────────────
import { useEffect, useState, useCallback } from 'react'
import { getAdminEvents, forcecancelEvent, getSellerApplications, processSellerApplication, runSettlementProcess, getAdminSettlements } from '../../api/admin.api'
import type { AdminEventItem, SellerApplicationListItem, SettlementItem } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

const EVENT_STATUS_MAP: Record<string, { label: string; cls: string }> = {
  DRAFT:     { label: '초안',    cls: 'badge-gray' },
  ON_SALE:   { label: '판매중',  cls: 'badge-green' },
  SOLD_OUT:  { label: '매진',    cls: 'badge-red' },
  ENDED:     { label: '종료',    cls: 'badge-gray' },
  CANCELLED: { label: '취소됨',  cls: 'badge-gray' },
}

export function AdminEvents() {
  const { toast } = useToast()
  const [events, setEvents] = useState<AdminEventItem[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')
  const [draftKeyword, setDraftKeyword] = useState('')
  const [actionLoading, setActionLoading] = useState<string | null>(null)

  const fetchEvents = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getAdminEvents({ keyword: keyword || undefined, page: 0, size: 50 })
      setEvents(res.data.content)
    } catch { toast('로드 실패', 'error') }
    finally { setLoading(false) }
  }, [keyword])

  useEffect(() => { fetchEvents() }, [fetchEvents])

  const handleForceCancel = async (eventId: string, title: string) => {
    if (!confirm(`"${title}" 이벤트를 강제 취소할까요?\n참여자 전원에게 자동 환불됩니다.`)) return
    setActionLoading(eventId)
    try {
      await forcecancelEvent(eventId)
      toast('강제 취소 처리되었습니다', 'success')
      fetchEvents()
    } catch { toast('처리 실패', 'error') }
    finally { setActionLoading(null) }
  }

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>이벤트 관리</h1>
      </div>

      <form onSubmit={e => { e.preventDefault(); setKeyword(draftKeyword) }}
        style={{ display: 'flex', gap: 8, marginBottom: 20, maxWidth: 400 }}>
        <div className="search-bar" style={{ flex: 1 }}>
          <svg className="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input className="search-input" placeholder="이벤트명 검색"
            value={draftKeyword} onChange={e => setDraftKeyword(e.target.value)} />
        </div>
        <button type="submit" className="btn btn-secondary">검색</button>
      </form>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>이벤트명</th>
                <th>판매자</th>
                <th>상태</th>
                <th>일시</th>
                <th style={{ textAlign: 'right' }}>총/잔여</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {events.map(event => {
                const status = EVENT_STATUS_MAP[event.status] ?? { label: event.status, cls: 'badge-gray' }
                return (
                  <tr key={event.eventId}>
                    <td>
                      <div style={{ fontWeight: 500, maxWidth: 240 }} className="truncate">{event.title}</div>
                    </td>
                    <td style={{ color: 'var(--text-3)', fontSize: 13 }}>{event.sellerNickname}</td>
                    <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                    <td style={{ fontSize: 13, color: 'var(--text-3)', whiteSpace: 'nowrap' }}>
                      {new Date(event.eventDateTime).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}
                    </td>
                    <td style={{ textAlign: 'right', fontSize: 13 }}>
                      {event.totalQuantity} / <span style={{ color: event.remainingQuantity === 0 ? 'var(--danger)' : 'inherit' }}>{event.remainingQuantity}</span>
                    </td>
                    <td>
                      {event.status === 'ON_SALE' && (
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={actionLoading === event.eventId}
                          onClick={() => handleForceCancel(event.eventId, event.title)}
                        >
                          {actionLoading === event.eventId ? '...' : '강제 취소'}
                        </button>
                      )}
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

// ── AdminApplications ──────────────────────────────────────────────────────────
export function AdminApplications() {
  const { toast } = useToast()
  const [apps, setApps] = useState<SellerApplicationListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<string | null>(null)

  const fetchApps = async () => {
    setLoading(true)
    try {
      const res = await getSellerApplications()
      setApps(res.data.data.content)
    } catch { toast('로드 실패', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchApps() }, [])

  const handle = async (id: string, approve: boolean) => {
    const label = approve ? '승인' : '반려'
    if (!confirm(`${label}하시겠습니까?`)) return
    setActionLoading(id)
    try {
      await processSellerApplication(id, approve)
      toast(`${label} 처리되었습니다`, 'success')
      fetchApps()
    } catch { toast('처리 실패', 'error') }
    finally { setActionLoading(null) }
  }

  const STATUS_MAP: Record<string, { label: string; cls: string }> = {
    PENDING:  { label: '대기 중', cls: 'badge-amber' },
    APPROVED: { label: '승인',    cls: 'badge-green' },
    REJECTED: { label: '반려',    cls: 'badge-red' },
  }

  const pendingCount = apps.filter(a => a.status === 'PENDING').length

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>판매자 신청 심사</h1>
        {pendingCount > 0 && (
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 10px', borderRadius: 'var(--r-full)', background: 'var(--warning-bg)', color: 'var(--warning-text)', fontSize: 13, fontWeight: 500 }}>
            ⚠️ 대기 중인 신청 {pendingCount}건
          </div>
        )}
      </div>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : apps.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">📋</div>
          <div className="empty-title">신청 내역이 없습니다</div>
        </div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>신청자</th>
                <th>상호명</th>
                <th>상태</th>
                <th>신청일</th>
                <th>처리</th>
              </tr>
            </thead>
            <tbody>
              {apps.map(app => {
                const status = STATUS_MAP[app.status] ?? { label: app.status, cls: 'badge-gray' }
                const isLoading = actionLoading === app.applicationId
                return (
                  <tr key={app.applicationId}>
                    <td>
                      <div style={{ fontWeight: 500 }}>{app.nickname}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-4)', fontFamily: 'var(--font-mono)' }}>
                        #{app.userId.slice(0, 10)}
                      </div>
                    </td>
                    <td style={{ color: 'var(--text-2)' }}>{app.businessName}</td>
                    <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                    <td style={{ fontSize: 13, color: 'var(--text-3)' }}>
                      {new Date(app.createdAt).toLocaleDateString('ko-KR')}
                    </td>
                    <td>
                      {app.status === 'PENDING' && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-primary btn-sm" disabled={isLoading}
                            onClick={() => handle(app.applicationId, true)}>
                            {isLoading ? '...' : '승인'}
                          </button>
                          <button className="btn btn-danger btn-sm" disabled={isLoading}
                            onClick={() => handle(app.applicationId, false)}>
                            {isLoading ? '...' : '반려'}
                          </button>
                        </div>
                      )}
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

// ── AdminSettlements ───────────────────────────────────────────────────────────
export function AdminSettlements() {
  const { toast } = useToast()
  const [settlements, setSettlements] = useState<SettlementItem[]>([])
  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)

  const fetchSettlements = async () => {
    setLoading(true)
    try {
      const res = await getAdminSettlements()
      setSettlements(res.data.content)
    } catch { toast('로드 실패', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchSettlements() }, [])

  const handleRun = async () => {
    if (!confirm('정산 프로세스를 실행할까요? 이 작업은 되돌릴 수 없습니다.')) return
    setRunning(true)
    try {
      await runSettlementProcess()
      toast('정산 프로세스가 실행되었습니다', 'success')
      fetchSettlements()
    } catch { toast('실행 실패', 'error') }
    finally { setRunning(false) }
  }

  const STATUS_MAP: Record<string, { label: string; cls: string }> = {
    PENDING:   { label: '대기', cls: 'badge-amber' },
    COMPLETED: { label: '완료', cls: 'badge-green' },
    CANCELLED: { label: '취소', cls: 'badge-gray' },
  }

  const totalNet = settlements.reduce((acc, s) => acc + s.netAmount, 0)

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>정산 관리</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)' }}>총 정산액: <strong>{totalNet.toLocaleString()}원</strong></p>
        </div>
        <button className="btn btn-primary" onClick={handleRun} disabled={running}>
          {running ? '실행 중...' : '⚡ 정산 프로세스 실행'}
        </button>
      </div>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : settlements.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">💰</div>
          <div className="empty-title">정산 내역이 없습니다</div>
        </div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>이벤트</th>
                <th style={{ textAlign: 'right' }}>판매액</th>
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
                    </td>
                    <td style={{ textAlign: 'right' }}>{s.settledAmount.toLocaleString()}원</td>
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

export default AdminEvents
