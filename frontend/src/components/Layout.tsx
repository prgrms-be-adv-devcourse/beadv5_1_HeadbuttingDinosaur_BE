import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { logout as apiLogout } from '../api/auth.api'
import { useState } from 'react'
import ThemeToggle from './ThemeToggle'

export default function Layout() {
  const { isLoggedIn, user, role, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = async () => {
    try { await apiLogout() } catch {}
    logout()
    navigate('/login')
  }

  return (
    <div>
      {/* GNB */}
      <header style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100,
        height: 'var(--nav-h)',
        background: 'rgba(255,255,255,0.92)',
        backdropFilter: 'blur(12px)',
        borderBottom: '1px solid var(--border)',
        display: 'flex', alignItems: 'center',
      }}>
        <div className="container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', maxWidth: 'var(--content-max)' }}>
          {/* Logo */}
          <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{
              width: 28, height: 28, borderRadius: 8,
              background: 'var(--brand)', display: 'flex',
              alignItems: 'center', justifyContent: 'center',
              color: '#fff', fontSize: 13, fontWeight: 700,
              fontFamily: 'var(--font-mono)',
            }}>DT</span>
            <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)' }}>DevTicket</span>
          </Link>

          {/* Nav links */}
          <nav style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <NavLink to="/" label="이벤트" current={location.pathname === '/'} />
            {isLoggedIn && <NavLink to="/cart" label="장바구니" current={location.pathname === '/cart'} />}
            {isLoggedIn && <NavLink to="/mypage" label="마이페이지" current={location.pathname.startsWith('/mypage')} />}
            {(role === 'SELLER' || role === 'ADMIN') && (
              <NavLink to="/seller" label="판매자" current={location.pathname.startsWith('/seller')} badge />
            )}
            {role === 'ADMIN' && (
              <NavLink to="/admin" label="관리자" current={location.pathname.startsWith('/admin')} admin />
            )}
          </nav>

          {/* Auth */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ThemeToggle size={34} />
            {isLoggedIn ? (
              <div style={{ position: 'relative' }}>
                <button
                  onClick={() => setMenuOpen(o => !o)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 8,
                    padding: '6px 12px', borderRadius: 'var(--r-md)',
                    border: '1px solid var(--border)', background: 'var(--surface)',
                    cursor: 'pointer', fontSize: 14, color: 'var(--text-2)',
                  }}
                >
                  <div style={{
                    width: 26, height: 26, borderRadius: '50%',
                    background: 'var(--brand-light)', color: 'var(--brand)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 12, fontWeight: 600,
                  }}>
                    {user?.nickname?.charAt(0).toUpperCase()}
                  </div>
                  {user?.nickname}
                </button>
                {menuOpen && (
                  <div style={{
                    position: 'absolute', top: 'calc(100% + 8px)', right: 0,
                    background: 'var(--surface)', border: '1px solid var(--border)',
                    borderRadius: 'var(--r-lg)', boxShadow: 'var(--shadow-lg)',
                    minWidth: 160, zIndex: 200, padding: 6,
                  }} onMouseLeave={() => setMenuOpen(false)}>
                    <DropItem to="/mypage" label="마이페이지" onClick={() => setMenuOpen(false)} />
                    <DropItem to="/mypage?tab=wallet" label="예치금 관리" onClick={() => setMenuOpen(false)} />
                    {role === 'USER' && (
                      <DropItem to="/seller-apply" label="판매자 신청" onClick={() => setMenuOpen(false)} />
                    )}
                    <div className="divider" style={{ margin: '4px 0' }} />
                    <button
                      onClick={handleLogout}
                      style={{
                        width: '100%', padding: '7px 12px',
                        borderRadius: 'var(--r-sm)', textAlign: 'left',
                        fontSize: 13, color: 'var(--danger)', cursor: 'pointer',
                        background: 'none', border: 'none',
                      }}
                    >로그아웃</button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <Link to="/login" className="btn btn-secondary btn-sm">로그인</Link>
                <Link to="/signup" className="btn btn-primary btn-sm">회원가입</Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="page-wrapper">
        <Outlet />
      </main>
    </div>
  )
}

function NavLink({ to, label, current, badge, admin }: {
  to: string; label: string; current: boolean; badge?: boolean; admin?: boolean
}) {
  return (
    <Link to={to} style={{
      display: 'flex', alignItems: 'center', gap: 4,
      padding: '5px 12px', borderRadius: 'var(--r-md)',
      fontSize: 14, fontWeight: current ? 500 : 400,
      color: current ? 'var(--brand)' : 'var(--text-2)',
      background: current ? 'var(--brand-light)' : 'transparent',
      transition: 'all 0.15s',
    }}>
      {label}
      {badge && !current && (
        <span style={{
          fontSize: 10, padding: '1px 5px', borderRadius: 99,
          background: 'var(--brand)', color: '#fff', fontWeight: 600,
        }}>PRO</span>
      )}
      {admin && !current && (
        <span style={{
          fontSize: 10, padding: '1px 5px', borderRadius: 99,
          background: '#0F172A', color: '#fff', fontWeight: 600,
        }}>ADMIN</span>
      )}
    </Link>
  )
}

function DropItem({ to, label, onClick }: { to: string; label: string; onClick: () => void }) {
  return (
    <Link to={to} onClick={onClick} style={{
      display: 'block', padding: '7px 12px',
      borderRadius: 'var(--r-sm)', fontSize: 13,
      color: 'var(--text-2)', transition: 'background 0.1s',
    }}
    onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-2)')}
    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
    >{label}</Link>
  )
}
