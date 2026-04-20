import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { logout as apiLogout } from '../api/auth.api'

const NAV = [
  { to: '/seller',                  label: '대시보드',      icon: '▦' },
  { to: '/seller/events/create',    label: '이벤트 등록',   icon: '+' },
  { to: '/seller/settlements',      label: '정산 내역',     icon: '₩' },
]

export default function SellerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try { await apiLogout() } catch {}
    logout()
    navigate('/login')
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar */}
      <aside style={{
        width: 'var(--sidebar-w)',
        flexShrink: 0,
        background: 'var(--surface)',
        borderRight: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        position: 'fixed',
        top: 0, left: 0, bottom: 0,
        zIndex: 50,
      }}>
        {/* Logo */}
        <Link to="/seller" style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '18px 20px',
          borderBottom: '1px solid var(--border)',
        }}>
          <span style={{
            width: 26, height: 26, borderRadius: 6,
            background: 'var(--brand)', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 12, fontWeight: 700, fontFamily: 'var(--font-mono)',
          }}>DT</span>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text)' }}>DevTicket</div>
            <div style={{ fontSize: 10, color: 'var(--brand)', fontWeight: 500 }}>판매자 센터</div>
          </div>
        </Link>

        {/* Nav */}
        <nav style={{ padding: '12px 12px', flex: 1 }}>
          <div style={{ fontSize: 10, fontWeight: 600, color: 'var(--text-4)', padding: '4px 8px 8px', letterSpacing: '0.08em' }}>MENU</div>
          {NAV.map(item => (
            <NavLink key={item.to} to={item.to} end={item.to === '/seller'} style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '9px 12px', borderRadius: 'var(--r-md)',
              fontSize: 14, fontWeight: isActive ? 500 : 400,
              color: isActive ? 'var(--brand)' : 'var(--text-2)',
              background: isActive ? 'var(--brand-light)' : 'transparent',
              marginBottom: 2,
              transition: 'all 0.12s',
            })}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 15, width: 18, textAlign: 'center' }}>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div style={{ padding: '16px', borderTop: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
            <div style={{
              width: 30, height: 30, borderRadius: '50%',
              background: 'var(--brand-light)', color: 'var(--brand)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 12, fontWeight: 600,
            }}>{user?.nickname?.charAt(0)?.toUpperCase()}</div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 500 }}>{user?.nickname}</div>
              <div style={{ fontSize: 11, color: 'var(--text-4)' }}>판매자</div>
            </div>
          </div>
          <Link to="/" style={{ display: 'block', fontSize: 13, color: 'var(--text-3)', marginBottom: 4 }}>← 메인으로</Link>
          <button onClick={handleLogout} style={{ fontSize: 13, color: 'var(--danger)', cursor: 'pointer', background: 'none', border: 'none' }}>로그아웃</button>
        </div>
      </aside>

      {/* Main content */}
      <main style={{ flex: 1, marginLeft: 'var(--sidebar-w)', minHeight: '100vh', background: 'var(--bg)' }}>
        <Outlet />
      </main>
    </div>
  )
}
