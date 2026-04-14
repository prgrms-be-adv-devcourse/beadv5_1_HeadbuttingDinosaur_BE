import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createProfile, getTechStacks } from '../api/auth.api'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'

interface TechStackItem {
  techStackId: number
  name: string
}

const POSITIONS = ['BACKEND', 'FRONTEND', 'FULLSTACK', 'DEVOPS', 'AI_ML', 'MOBILE', 'OTHER']
const POSITION_LABELS: Record<string, string> = {
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택',
  DEVOPS: 'DevOps/인프라', AI_ML: 'AI/ML', MOBILE: '모바일', OTHER: '기타',
}

export default function SocialProfileSetup() {
  const { login, isLoggedIn } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [techStacks, setTechStacks] = useState<TechStackItem[]>([])
  const [form, setForm] = useState({ nickname: '', position: '', selectedStackIds: [] as number[] })

  // 토큰 없으면 로그인으로
  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/login', { replace: true })
      return
    }
    getTechStacks()
      .then(res => setTechStacks(res.data.data.techStacks))
      .catch(() => {
        const fallbacks = ['Java','Spring Boot','Kotlin','JavaScript','TypeScript','React','Vue.js',
          'Node.js','Python','FastAPI','Go','Rust','Docker','Kubernetes','AWS','MySQL','PostgreSQL',
          'Redis','Kafka','ElasticSearch']
        setTechStacks(fallbacks.map((name, i) => ({ techStackId: i + 1, name })))
      })
  }, [navigate])

  // 이미 로그인 완료된 사용자(프로필 있음)는 홈으로
  useEffect(() => {
    if (isLoggedIn) navigate('/', { replace: true })
  }, [isLoggedIn, navigate])

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.nickname) e.nickname = '닉네임을 입력하세요'
    else if (form.nickname.length < 2 || form.nickname.length > 12) e.nickname = '닉네임은 2~12자여야 합니다'
    if (!form.position) e.position = '포지션을 선택하세요'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      await createProfile({
        nickname: form.nickname,
        position: form.position,
        techStackIds: form.selectedStackIds,
        profileImageUrl: null,
        bio: null,
      })

      // 콜백에서 저장한 소셜 accessToken으로 바로 로그인
      // refreshToken은 콜백에서 이미 초기화되어 있으므로 사용하지 않음
      const accessToken = localStorage.getItem('accessToken')!
      await login(accessToken, '')
      navigate('/signup/complete', {
        replace: true,
        state: {
          nickname: form.nickname,
          position: form.position,
          stacks: form.selectedStackIds.map(id => {
            const found = techStacks.find(s => s.techStackId === id)
            return found?.name ?? ''
          }).filter(Boolean),
        },
      })
    } catch {
      toast('프로필 저장에 실패했습니다. 다시 시도해주세요.', 'error')
    } finally {
      setLoading(false)
    }
  }

  const toggleStack = (id: number) => {
    setForm(p => ({
      ...p,
      selectedStackIds: p.selectedStackIds.includes(id)
        ? p.selectedStackIds.filter(s => s !== id)
        : [...p.selectedStackIds, id],
    }))
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)', padding: '24px',
    }}>
      <div style={{ width: '100%', maxWidth: 480 }}>
        {/* 헤더 */}
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

          {/* Google 아이콘 배지 */}
          <div style={{
            width: 48, height: 48, borderRadius: '50%',
            border: '2px solid var(--border)', background: 'var(--surface)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 16px', fontSize: 22,
          }}>
            <GoogleIcon />
          </div>

          <h1 style={{ fontSize: 22, fontWeight: 600 }}>프로필 설정</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)', marginTop: 6, lineHeight: 1.5 }}>
            Google 계정으로 가입되었습니다.<br />
            서비스 이용을 위해 프로필을 완성해주세요.
          </p>
        </div>

        <div className="card" style={{ padding: '28px' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* 닉네임 */}
            <div className="form-group">
              <label className="form-label">
                닉네임 <span style={{ color: 'var(--danger)' }}>*</span>
              </label>
              <input
                className="form-input"
                type="text"
                placeholder="2~12자"
                value={form.nickname}
                onChange={e => setForm(p => ({ ...p, nickname: e.target.value }))}
                style={errors.nickname ? { borderColor: 'var(--danger)' } : {}}
              />
              {errors.nickname && <span className="form-error">{errors.nickname}</span>}
            </div>

            {/* 포지션 */}
            <div className="form-group">
              <label className="form-label">
                포지션 <span style={{ color: 'var(--danger)' }}>*</span>
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
                {POSITIONS.map(pos => (
                  <button
                    key={pos}
                    type="button"
                    onClick={() => { setForm(p => ({ ...p, position: pos })); setErrors(prev => ({ ...prev, position: '' })) }}
                    style={{
                      padding: '8px 10px', borderRadius: 'var(--r-md)', fontSize: 13,
                      border: `1px solid ${form.position === pos ? 'var(--brand)' : 'var(--border)'}`,
                      background: form.position === pos ? 'var(--brand-light)' : 'var(--surface)',
                      color: form.position === pos ? 'var(--brand)' : 'var(--text-2)',
                      cursor: 'pointer', transition: 'all 0.12s', textAlign: 'left',
                    }}
                  >
                    {POSITION_LABELS[pos] ?? pos}
                  </button>
                ))}
              </div>
              {errors.position && <span className="form-error">{errors.position}</span>}
            </div>

            {/* 기술 스택 */}
            <div className="form-group">
              <label className="form-label">
                관심 기술 스택{' '}
                <span style={{ color: 'var(--text-4)', fontWeight: 400 }}>(선택)</span>
              </label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {techStacks.map(stack => (
                  <button
                    key={stack.techStackId}
                    type="button"
                    onClick={() => toggleStack(stack.techStackId)}
                    className="tag"
                    style={{
                      cursor: 'pointer',
                      background: form.selectedStackIds.includes(stack.techStackId)
                        ? 'var(--brand-light)' : 'var(--surface-2)',
                      color: form.selectedStackIds.includes(stack.techStackId)
                        ? 'var(--brand)' : 'var(--text-2)',
                      border: `1px solid ${form.selectedStackIds.includes(stack.techStackId)
                        ? 'var(--brand-muted)' : 'var(--border)'}`,
                    }}
                  >
                    {stack.name}
                  </button>
                ))}
              </div>
              {form.selectedStackIds.length > 0 && (
                <div style={{ fontSize: 12, color: 'var(--text-3)', marginTop: 4 }}>
                  {form.selectedStackIds.length}개 선택됨
                </div>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-full btn-lg"
              disabled={loading}
              style={{ marginTop: 4 }}
            >
              {loading ? '저장 중...' : '프로필 완성하기'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M43.611 20.083H42V20H24v8h11.303C33.654 32.657 29.332 36 24 36c-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" fill="#FFC107"/>
      <path d="M6.306 14.691l6.571 4.819C14.655 15.108 19.000 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z" fill="#FF3D00"/>
      <path d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.316 0-9.828-3.417-11.402-8.162l-6.515 5.021C9.505 39.556 16.227 44 24 44z" fill="#4CAF50"/>
      <path d="M43.611 20.083H42V20H24v8h11.303c-.792 2.237-2.231 4.166-4.087 5.571.001-.001.002-.001.003-.002l6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" fill="#1976D2"/>
    </svg>
  )
}
