import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { signup, createProfile, getTechStacks, reissueToken } from '../api/auth.api'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'

const GOOGLE_OAUTH_URL = import.meta.env.VITE_GOOGLE_OAUTH_URL ?? 'http://localhost:8080/oauth2/authorization/google'

interface TechStackItem {
  techStackId: number
  name: string
}

const POSITIONS = ['BACKEND', 'FRONTEND', 'FULLSTACK', 'DEVOPS', 'AI_ML', 'MOBILE', 'OTHER']
const POSITION_LABELS: Record<string, string> = {
  BACKEND: '백엔드', FRONTEND: '프론트엔드', FULLSTACK: '풀스택',
  DEVOPS: 'DevOps/인프라', AI_ML: 'AI/ML', MOBILE: '모바일', OTHER: '기타',
}

export default function Signup() {
  const { login } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [techStacks, setTechStacks] = useState<TechStackItem[]>([])

  // Step 1: 계정 정보 (백엔드 SignUpRequest: email, password, passwordConfirm)
  const [step1, setStep1] = useState({ email: '', password: '', passwordConfirm: '' })

  // Step 2: 프로필 정보 (백엔드 SignUpProfileRequest: nickname, position, techStackIds, profileImageUrl, bio)
  const [step2, setStep2] = useState({ nickname: '', position: '', selectedStackIds: [] as number[] })

  // signup 후 받은 토큰 임시 저장
  const [signupResult, setSignupResult] = useState<{ accessToken: string; refreshToken: string } | null>(null)

  useEffect(() => {
    getTechStacks().then(res => {
      setTechStacks(res.data.data.techStacks)
    }).catch(() => {
      const fallbacks = ['Java','Spring Boot','Kotlin','JavaScript','TypeScript','React','Vue.js','Node.js','Python','FastAPI','Go','Rust','Docker','Kubernetes','AWS','MySQL','PostgreSQL','Redis','Kafka','ElasticSearch']
      setTechStacks(fallbacks.map((name, i) => ({ techStackId: i + 1, name })))
    })
  }, [])

  // ─── Step 1 검증 ───
  const validateStep1 = () => {
    const e: Record<string, string> = {}
    if (!step1.email) e.email = '이메일을 입력하세요'
    else if (!/\S+@\S+\.\S+/.test(step1.email)) e.email = '올바른 이메일 형식이 아닙니다'
    if (!step1.password) e.password = '비밀번호를 입력하세요'
    else if (step1.password.length < 8) e.password = '비밀번호는 8자 이상이어야 합니다'
    if (!step1.passwordConfirm) e.passwordConfirm = '비밀번호 확인을 입력하세요'
    else if (step1.password !== step1.passwordConfirm) e.passwordConfirm = '비밀번호가 일치하지 않습니다'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  // ─── Step 1 제출: POST /api/auth/signup ───
  const handleStep1 = async (e: React.FormEvent) => {
  e.preventDefault()
  console.log('1. handleStep1 진입')
  if (!validateStep1()) {
    console.log('2. validation 실패', errors)
    return
  }
  console.log('3. validation 통과')
  setLoading(true)
  try {
    const res = await signup({
      email: step1.email,
      password: step1.password,
      passwordConfirm: step1.passwordConfirm,
    })
    console.log('4. signup 응답:', res.data)
    const data = res.data as any
    console.log('5. data:', data)
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    setStep(2)
  } catch (err) {
    console.error('6. 에러:', err)
    toast('회원가입 실패. 이미 사용 중인 이메일일 수 있습니다.', 'error')
  } finally {
    setLoading(false)
  }
}

  // ─── Step 2 검증 ───
  const validateStep2 = () => {
    const e: Record<string, string> = {}
    if (!step2.nickname) e.nickname = '닉네임을 입력하세요'
    else if (step2.nickname.length < 2 || step2.nickname.length > 12) e.nickname = '닉네임은 2~12자여야 합니다'
    if (!step2.position) e.position = '포지션을 선택하세요'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  // ─── Step 2 제출: POST /api/users/profile ───
  const handleStep2 = async (e: React.FormEvent) => {
  e.preventDefault()
  if (!validateStep2()) return
  setLoading(true)
  try {
    await createProfile({
      nickname: step2.nickname,
      position: step2.position,
      techStackIds: step2.selectedStackIds,
      profileImageUrl: null,
      bio: null,
    })
    // 프로필 생성 완료 → 이제 getProfile 성공함
    const rt = localStorage.getItem('refreshToken')!
    const reissueRes = await reissueToken({ refreshToken: rt })
    const newData = reissueRes.data.data ?? reissueRes.data as any
    localStorage.setItem('accessToken', newData.accessToken)
    localStorage.setItem('refreshToken', newData.refreshToken)

    await login(newData.accessToken, newData.refreshToken)
    navigate('/signup/complete', {
  state: {
    nickname: step2.nickname,
    position: step2.position,
    stacks: step2.selectedStackIds.map(id => {
      const found = techStacks.find(s => s.techStackId === id)
      return found?.name ?? ''
    }).filter(Boolean),
  },
})
  } catch {
    toast('프로필 저장 실패', 'error')
  } finally {
    setLoading(false)
  }
}

  const toggleStack = (id: number) => {
    setStep2(p => ({
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
        {/* Logo + Progress */}
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

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0, marginBottom: 24 }}>
            {[1, 2].map(s => (
              <div key={s} style={{ display: 'flex', alignItems: 'center' }}>
                <div style={{
                  width: 28, height: 28, borderRadius: '50%',
                  background: step >= s ? 'var(--brand)' : 'var(--surface-2)',
                  color: step >= s ? '#fff' : 'var(--text-3)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 12, fontWeight: 600,
                }}>{s}</div>
                {s < 2 && <div style={{ width: 48, height: 2, background: step > s ? 'var(--brand)' : 'var(--surface-3)' }} />}
              </div>
            ))}
          </div>

          <h1 style={{ fontSize: 22, fontWeight: 600 }}>
            {step === 1 ? '계정 만들기' : '프로필 설정'}
          </h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)', marginTop: 4 }}>
            {step === 1 ? '기본 정보를 입력해주세요' : '관심 분야를 선택하면 더 나은 추천을 받을 수 있어요'}
          </p>
        </div>

        <div className="card" style={{ padding: '28px' }}>
          {step === 1 ? (
            <form onSubmit={handleStep1} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div className="form-group">
                <label className="form-label">이메일</label>
                <input className="form-input" type="email" placeholder="you@example.com"
                  value={step1.email} onChange={e => setStep1(f => ({ ...f, email: e.target.value }))}
                  style={errors.email ? { borderColor: 'var(--danger)' } : {}} />
                {errors.email && <span className="form-error">{errors.email}</span>}
              </div>

              <div className="form-group">
                <label className="form-label">비밀번호</label>
                <input className="form-input" type="password" placeholder="8자 이상"
                  value={step1.password} onChange={e => setStep1(f => ({ ...f, password: e.target.value }))}
                  style={errors.password ? { borderColor: 'var(--danger)' } : {}} />
                {errors.password && <span className="form-error">{errors.password}</span>}
              </div>

              <div className="form-group">
                <label className="form-label">비밀번호 확인</label>
                <input className="form-input" type="password" placeholder="비밀번호 재입력"
                  value={step1.passwordConfirm} onChange={e => setStep1(f => ({ ...f, passwordConfirm: e.target.value }))}
                  style={errors.passwordConfirm ? { borderColor: 'var(--danger)' } : {}} />
                {errors.passwordConfirm && <span className="form-error">{errors.passwordConfirm}</span>}
              </div>

              <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading} style={{ marginTop: 4 }}>
                {loading ? '처리 중...' : '다음 단계 →'}
              </button>

              {/* 구분선 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
                <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
                <span style={{ fontSize: 12, color: 'var(--text-4)', whiteSpace: 'nowrap' }}>또는</span>
                <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
              </div>

              {/* 구글 회원가입 버튼 */}
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
                Google로 회원가입
              </button>
            </form>
          ) : (
            <form onSubmit={handleStep2} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              {/* 닉네임 */}
              <div className="form-group">
                <label className="form-label">닉네임 <span style={{ color: 'var(--danger)' }}>*</span></label>
                <input className="form-input" type="text" placeholder="2~12자"
                  value={step2.nickname} onChange={e => setStep2(p => ({ ...p, nickname: e.target.value }))}
                  style={errors.nickname ? { borderColor: 'var(--danger)' } : {}} />
                {errors.nickname && <span className="form-error">{errors.nickname}</span>}
              </div>

              {/* 포지션 */}
              <div className="form-group">
                <label className="form-label">포지션 <span style={{ color: 'var(--danger)' }}>*</span></label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
                  {POSITIONS.map(pos => (
                    <button key={pos} type="button"
                      onClick={() => { setStep2(p => ({ ...p, position: pos })); setErrors({}) }}
                      style={{
                        padding: '8px 10px', borderRadius: 'var(--r-md)', fontSize: 13,
                        border: `1px solid ${step2.position === pos ? 'var(--brand)' : 'var(--border)'}`,
                        background: step2.position === pos ? 'var(--brand-light)' : 'var(--surface)',
                        color: step2.position === pos ? 'var(--brand)' : 'var(--text-2)',
                        cursor: 'pointer', transition: 'all 0.12s', textAlign: 'left',
                      }}
                    >{POSITION_LABELS[pos] ?? pos}</button>
                  ))}
                </div>
                {errors.position && <span className="form-error">{errors.position}</span>}
              </div>

              {/* 기술 스택 */}
              <div className="form-group">
                <label className="form-label">관심 기술 스택 <span style={{ color: 'var(--text-4)', fontWeight: 400 }}>(선택)</span></label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {techStacks.map(stack => (
                    <button key={stack.techStackId} type="button"
                      onClick={() => toggleStack(stack.techStackId)}
                      className="tag"
                      style={{
                        cursor: 'pointer',
                        background: step2.selectedStackIds.includes(stack.techStackId) ? 'var(--brand-light)' : 'var(--surface-2)',
                        color: step2.selectedStackIds.includes(stack.techStackId) ? 'var(--brand)' : 'var(--text-2)',
                        border: `1px solid ${step2.selectedStackIds.includes(stack.techStackId) ? 'var(--brand-muted)' : 'var(--border)'}`,
                      }}
                    >{stack.name}</button>
                  ))}
                </div>
                {step2.selectedStackIds.length > 0 && (
                  <div style={{ fontSize: 12, color: 'var(--text-3)', marginTop: 4 }}>
                    {step2.selectedStackIds.length}개 선택됨
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button type="button" className="btn btn-secondary" style={{ flex: 1, justifyContent: 'center' }}
                  onClick={() => setStep(1)}>← 이전</button>
                <button type="submit" className="btn btn-primary" style={{ flex: 2, justifyContent: 'center' }} disabled={loading}>
                  {loading ? '처리 중...' : '가입 완료'}
                </button>
              </div>
            </form>
          )}
        </div>

        <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--text-3)', marginTop: 20 }}>
          이미 계정이 있으신가요?{' '}
          <Link to="/login" style={{ color: 'var(--brand)', fontWeight: 500 }}>로그인</Link>
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