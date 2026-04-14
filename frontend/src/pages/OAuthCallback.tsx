import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { getProfile } from '../api/auth.api'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'
import axios from 'axios'

const ERROR_MESSAGES: Record<string, string> = {
  SOCIAL_EMAIL_CONFLICT: '이미 해당 이메일로 가입된 로컬 계정이 있습니다. 이메일/비밀번호로 로그인해주세요.',
  DEFAULT: '소셜 로그인에 실패했습니다. 다시 시도해주세요.',
}

export default function OAuthCallback() {
  const { login } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    const error = params.get('error')

    if (error) {
      const msg = ERROR_MESSAGES[error] ?? ERROR_MESSAGES.DEFAULT
      setErrorMsg(msg)
      return
    }

    if (!token) {
      setErrorMsg(ERROR_MESSAGES.DEFAULT)
      return
    }

    // 토큰 저장 후 프로필 조회
    localStorage.setItem('accessToken', token)

    getProfile()
      .then(async (res) => {
        const profile = res.data
        const needsProfileSetup =
          profile.providerType === 'GOOGLE' &&
          (!profile.nickname || !profile.position)

        if (needsProfileSetup) {
          // 신규 구글 가입자 → 프로필 작성 페이지로
          navigate('/social/profile-setup', { replace: true })
        } else {
          // 기존 구글 사용자 → 로그인 처리 후 홈으로
          await login(token, '')
          toast('로그인되었습니다.', 'success')
          navigate('/', { replace: true })
        }
      })
      .catch((err) => {
        // 403: 프로필 미완성 상태 → 토큰은 유지하고 프로필 설정 페이지로
        if (axios.isAxiosError(err) && err.response?.status === 403) {
          navigate('/social/profile-setup', { replace: true })
          return
        }
        localStorage.removeItem('accessToken')
        setErrorMsg(ERROR_MESSAGES.DEFAULT)
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (errorMsg) {
    return (
      <div style={{
        minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: 'var(--bg)', padding: '24px',
      }}>
        <div style={{ width: '100%', maxWidth: 420, textAlign: 'center' }}>
          <div style={{
            width: 52, height: 52, borderRadius: '50%',
            background: 'var(--danger-light, #fee2e2)', margin: '0 auto 20px',
            display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24,
          }}>!</div>
          <h2 style={{ fontSize: 18, fontWeight: 600, marginBottom: 12 }}>로그인 실패</h2>
          <p style={{ fontSize: 14, color: 'var(--text-3)', marginBottom: 28, lineHeight: 1.6 }}>
            {errorMsg}
          </p>
          <Link to="/login" className="btn btn-primary" style={{ display: 'inline-flex' }}>
            로그인 페이지로 돌아가기
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'var(--bg)',
    }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{
          width: 36, height: 36, border: '3px solid var(--brand)',
          borderTopColor: 'transparent', borderRadius: '50%',
          animation: 'spin 0.8s linear infinite', margin: '0 auto 16px',
        }} />
        <p style={{ fontSize: 14, color: 'var(--text-3)' }}>Google 로그인 처리 중...</p>
      </div>
    </div>
  )
}
