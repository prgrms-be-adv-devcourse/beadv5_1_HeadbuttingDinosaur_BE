// ── SignupComplete ─────────────────────────────────────────────────────────────
import { useLocation, useNavigate, Link } from 'react-router-dom'

export default function SignupComplete() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { nickname: string; position: string; stacks: string[] } | null

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)', padding: 24,
    }}>
      <div style={{ textAlign: 'center', maxWidth: 420 }}>
        <div style={{
          width: 72, height: 72, borderRadius: '50%',
          background: 'var(--success-bg)', color: 'var(--success)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 32, margin: '0 auto 24px',
        }}>✓</div>
        <h1 style={{ fontSize: 26, fontWeight: 700, marginBottom: 8 }}>
          환영합니다, {state?.nickname}님! 🎉
        </h1>
        <p style={{ fontSize: 15, color: 'var(--text-3)', marginBottom: 28, lineHeight: 1.7 }}>
          DevTicket 회원이 되셨습니다.<br />
          이제 다양한 개발자 이벤트를 만나보세요.
        </p>
        {state?.stacks && state.stacks.length > 0 && (
          <div style={{ marginBottom: 28, display: 'flex', flexWrap: 'wrap', gap: 6, justifyContent: 'center' }}>
            {state.stacks.map(s => <span key={s} className="tag">{s}</span>)}
          </div>
        )}
        <button className="btn btn-primary btn-lg btn-full" onClick={() => navigate('/')}>
          이벤트 둘러보기 →
        </button>
      </div>
    </div>
  )
}
