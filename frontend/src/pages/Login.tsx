import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login as loginApi } from '../api/auth.api'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'

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
        </div>

        <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--text-3)', marginTop: 20 }}>
          아직 계정이 없으신가요?{' '}
          <Link to="/signup" style={{ color: 'var(--brand)', fontWeight: 500 }}>회원가입</Link>
        </p>
      </div>
    </div>
  )
}