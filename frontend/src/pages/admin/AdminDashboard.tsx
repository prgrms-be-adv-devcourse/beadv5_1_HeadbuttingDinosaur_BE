import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminDashboard } from '../../api/admin.api'
import type { AdminDashboardResponse } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

export default function AdminDashboard() {
  const { toast } = useToast()
  const [stats, setStats] = useState<AdminDashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getAdminDashboard()
      .then(r => setStats(r.data.data))
      .catch(() => toast('로드 실패', 'error'))
      .finally(() => setLoading(false))
  }, [])

  const CARDS = stats ? [
    { label: '전체 회원',        value: stats.totalUsers.toLocaleString(),       sub: '활성 기준',    color: 'var(--brand)',   link: '/admin/users' },
    { label: '판매자',           value: stats.totalSellers.toLocaleString(),      sub: '승인된 판매자', color: '#7C3AED',        link: '/admin/users' },
    { label: '판매중 이벤트',    value: stats.activeEvents.toLocaleString(),      sub: 'ON_SALE 상태', color: 'var(--success)', link: '/admin/events' },
    { label: '판매자 신청 대기', value: stats.pendingApplications.toLocaleString(), sub: '즉시 처리 필요', color: stats.pendingApplications > 0 ? 'var(--danger)' : 'var(--text-3)', link: '/admin/applications' },
  ] : []

  const SHORTCUTS = [
    { to: '/admin/users',        label: '회원 관리',     icon: '👥', desc: '회원 조회·제재·권한 변경' },
    { to: '/admin/events',       label: '이벤트 관리',   icon: '🎫', desc: '이벤트 조회·강제 취소' },
    { to: '/admin/applications', label: '판매자 심사',   icon: '📋', desc: '신청 승인·반려' },
    { to: '/admin/settlements',  label: '정산 실행',     icon: '⚡', desc: '정산 프로세스 실행·조회' },
  ]

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ marginBottom: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>관리자 대시보드</h1>
        <p style={{ fontSize: 14, color: 'var(--text-3)' }}>{new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })}</p>
      </div>

      {/* Stats */}
      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 32 }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} style={{ height: 100, borderRadius: 'var(--r-lg)', background: 'var(--surface-2)', animation: 'pulse 1.5s infinite' }} />
          ))}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 32 }}>
          {CARDS.map(card => (
            <Link key={card.label} to={card.link} style={{ textDecoration: 'none' }}>
              <div className="stat-card" style={{ cursor: 'pointer', transition: 'box-shadow 0.15s' }}
                onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-md)'}
                onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = ''}
              >
                <div className="stat-label">{card.label}</div>
                <div className="stat-value" style={{ color: card.color }}>{card.value}</div>
                <div className="stat-sub">{card.sub}</div>
              </div>
            </Link>
          ))}
        </div>
      )}

      {/* Shortcuts */}
      <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 14 }}>빠른 이동</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
        {SHORTCUTS.map(s => (
          <Link key={s.to} to={s.to} style={{ textDecoration: 'none' }}>
            <div className="card" style={{
              padding: '18px 20px', display: 'flex', alignItems: 'center', gap: 14,
              cursor: 'pointer', transition: 'box-shadow 0.15s, transform 0.15s',
            }}
            onMouseEnter={e => {
              ;(e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-md)'
              ;(e.currentTarget as HTMLElement).style.transform = 'translateY(-1px)'
            }}
            onMouseLeave={e => {
              ;(e.currentTarget as HTMLElement).style.boxShadow = ''
              ;(e.currentTarget as HTMLElement).style.transform = ''
            }}>
              <div style={{
                width: 44, height: 44, borderRadius: 'var(--r-md)',
                background: 'var(--surface-2)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 20, flexShrink: 0,
              }}>{s.icon}</div>
              <div>
                <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--text)', marginBottom: 2 }}>{s.label}</div>
                <div style={{ fontSize: 13, color: 'var(--text-3)' }}>{s.desc}</div>
              </div>
              <div style={{ marginLeft: 'auto', color: 'var(--text-4)', fontSize: 18 }}>›</div>
            </div>
          </Link>
        ))}
      </div>

      <style>{`@keyframes pulse { 0%, 100% { opacity: 1 } 50% { opacity: 0.5 } }`}</style>
    </div>
  )
}
