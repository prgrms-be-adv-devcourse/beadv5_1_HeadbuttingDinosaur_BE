import { Link, useNavigate } from 'react-router-dom'

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexDirection: 'column', gap: 16, padding: 24, textAlign: 'center',
    }}>
      <div style={{
        fontFamily: 'var(--font-mono)', fontSize: 96, fontWeight: 700,
        color: 'var(--surface-3)', lineHeight: 1, marginBottom: 8,
        letterSpacing: '-0.04em',
      }}>404</div>

      <h1 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text)' }}>
        페이지를 찾을 수 없습니다
      </h1>
      <p style={{ fontSize: 14, color: 'var(--text-3)', maxWidth: 320 }}>
        요청하신 페이지가 없거나 이동되었습니다.
      </p>

      <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
        <button onClick={() => navigate(-1)} className="btn btn-secondary">
          ← 이전으로
        </button>
        <Link to="/" className="btn btn-primary">
          홈으로
        </Link>
      </div>
    </div>
  )
}
