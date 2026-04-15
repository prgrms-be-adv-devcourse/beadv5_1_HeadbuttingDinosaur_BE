import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login as loginApi } from '../api/auth.api'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'

const GOOGLE_OAUTH_URL = import.meta.env.VITE_GOOGLE_OAUTH_URL ?? 'http://localhost:8080/oauth2/authorization/google'

export default function Login() {
  const { login } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.email) e.email = '이메일을 입력하세요'
    else if (!/\S+@\S+\.\S+/.test(form.email)) e.email = '올바른 이메일 형식이 아닙니다'
    if (!form.password) e.password = '비밀번호를 입력하세요'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const res = await loginApi({ email: form.email, password: form.password })
      const data = res.data as any
      await login(data.accessToken, data.refreshToken)
      navigate('/')
    } catch {
      toast('로그인 실패. 이메일 또는 비밀번호를 확인해주세요.', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)', padding: '24px',
    }}>
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 8, marginBottom: 20 }}>
            <span style={{
              width: 34, height: 34, borderRadius: 10,
              background: 'var(--brand)', color: '#fff',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 14, fontWeight: 700, fontFamily: 'var(--font-mono)',
            }}>DT</span>
            <span style={{ fontSize: 18, fontWeight: 700 }}>DevTicket</span>
          </Link>
          <h1 style={{ fontSize: 22, fontWeight: 600 }}>로그인</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)', marginTop: 4 }}>
            계정에 로그인하세요
          </p>
        </div>

        <div className="card" style={{ padding: '28px' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="form-group">
              <label className="form-label">이메일</label>
              <input className="form-input" type="email" placeholder="you@example.com"
                value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                style={errors.email ? { borderColor: 'var(--danger)' } : {}} />
              {errors.email && <span className="form-error">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">비밀번호</label>
              <input className="form-input" type="password" placeholder="비밀번호 입력"
                value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                style={errors.password ? { borderColor: 'var(--danger)' } : {}} />
              {errors.password && <span className="form-error">{errors.password}</span>}
            </div>

            <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading} style={{ marginTop: 4 }}>
              {loading ? '로그인 중...' : '로그인'}
            </button>
          </form>

          {/* 구분선 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '20px 0' }}>
            <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
            <span style={{ fontSize: 12, color: 'var(--text-4)', whiteSpace: 'nowrap' }}>또는</span>
            <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
          </div>

          {/* 구글 로그인 버튼 */}
          <button
            type="button"
            onClick={() => { window.location.href = GOOGLE_OAUTH_URL }}
            style={{
              width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center',
              gap: 10, padding: '10px 16px', borderRadius: 'var(--r-md)',
              border: '1px solid var(--border)', background: 'var(--surface)',
              color: 'var(--text-1)', fontSize: 14, fontWeight: 500,
              cursor: 'pointer', transition: 'background 0.12s',
            }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-2)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'var(--surface)')}
          >
            <GoogleIcon />
            Google로 로그인
          </button>
        </div>

        <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--text-3)', marginTop: 20 }}>
          아직 계정이 없으신가요?{' '}
          <Link to="/signup" style={{ color: 'var(--brand)', fontWeight: 500 }}>회원가입</Link>
        </p>
      </div>
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M43.611 20.083H42V20H24v8h11.303C33.654 32.657 29.332 36 24 36c-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" fill="#FFC107"/>
      <path d="M6.306 14.691l6.571 4.819C14.655 15.108 19.000 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z" fill="#FF3D00"/>
      <path d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.316 0-9.828-3.417-11.402-8.162l-6.515 5.021C9.505 39.556 16.227 44 24 44z" fill="#4CAF50"/>
      <path d="M43.611 20.083H42V20H24v8h11.303c-.792 2.237-2.231 4.166-4.087 5.571.001-.001.002-.001.003-.002l6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" fill="#1976D2"/>
    </svg>
  )
}