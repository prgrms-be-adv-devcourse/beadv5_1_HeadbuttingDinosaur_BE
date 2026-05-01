import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { logout as apiLogout } from '../api/auth.api'

const NAV = [
  { to: '/admin',               label: '대시보드',       icon: '▦' },
  { to: '/admin/users',         label: '회원 관리',      icon: '👤' },
  { to: '/admin/events',        label: '이벤트 관리',    icon: '🎫' },
  { to: '/admin/applications',  label: '판매자 심사',    icon: '📋' },
  { to: '/admin/settlements',   label: '정산 관리',      icon: '₩' },
]

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try { await apiLogout() } catch {}
    logout()
    navigate('/login')
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={{
        width: 'var(--sidebar-w)',
        flexShrink: 0,
        background: '#0F172A',
        display: 'flex',
        flexDirection: 'column',
        position: 'fixed',
        top: 0, left: 0, bottom: 0,
        zIndex: 50,
      }}>
        <Link to="/admin" style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '18px 20px',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
        }}>
          <span style={{
            width: 26, height: 26, borderRadius: 6,
            background: '#4F46E5', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 12, fontWeight: 700,
          }}>DT</span>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#F1F5F9' }}>DevTicket</div>
            <div style={{ fontSize: 10, color: '#818CF8', fontWeight: 500 }}>관리자 패널</div>
          </div>
        </Link>

        <nav style={{ padding: '12px', flex: 1 }}>
          <div style={{ fontSize: 10, fontWeight: 600, color: 'rgba(255,255,255,0.3)', padding: '4px 8px 8px', letterSpacing: '0.08em' }}>ADMIN</div>
          {NAV.map(item => (
            <NavLink key={item.to} to={item.to} end={item.to === '/admin'} style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '9px 12px', borderRadius: 'var(--r-md)',
              fontSize: 14, fontWeight: isActive ? 500 : 400,
              color: isActive ? '#fff' : 'rgba(255,255,255,0.55)',
              background: isActive ? 'rgba(79,70,229,0.35)' : 'transparent',
              marginBottom: 2,
              transition: 'all 0.12s',
            })}>
              <span style={{ fontSize: 14, width: 18, textAlign: 'center' }}>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div style={{ padding: '16px', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
            <div style={{
              width: 30, height: 30, borderRadius: '50%',
              background: 'rgba(79,70,229,0.3)', color: '#818CF8',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 12, fontWeight: 600,
            }}>{user?.nickname?.charAt(0)?.toUpperCase()}</div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 500, color: '#F1F5F9' }}>{user?.nickname}</div>
              <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)' }}>관리자</div>
            </div>
          </div>
          <Link to="/" style={{ display: 'block', fontSize: 13, color: 'rgba(255,255,255,0.4)', marginBottom: 4 }}>← 메인으로</Link>
          <button onClick={handleLogout} style={{ fontSize: 13, color: '#F87171', cursor: 'pointer', background: 'none', border: 'none' }}>로그아웃</button>
        </div>
      </aside>

      <main style={{ flex: 1, marginLeft: 'var(--sidebar-w)', minHeight: '100vh', background: 'var(--bg)' }}>
        <Outlet />
      </main>
    </div>
  )
}
