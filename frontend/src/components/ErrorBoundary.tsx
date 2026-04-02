import React from 'react'

interface Props {
  children: React.ReactNode
  fallback?: React.ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback

      return (
        <div style={{
          minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexDirection: 'column', gap: 16, padding: 24, textAlign: 'center',
        }}>
          <div style={{ fontSize: 48 }}>💥</div>
          <h1 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text)' }}>예상치 못한 오류가 발생했습니다</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)', maxWidth: 400 }}>
            {this.state.error?.message ?? '알 수 없는 오류입니다'}
          </p>
          <div style={{ display: 'flex', gap: 10 }}>
            <button
              onClick={() => this.setState({ hasError: false, error: null })}
              className="btn btn-secondary"
            >다시 시도</button>
            <button
              onClick={() => window.location.href = '/'}
              className="btn btn-primary"
            >홈으로</button>
          </div>
          {import.meta.env.DEV && this.state.error && (
            <pre style={{
              marginTop: 20, padding: '12px 16px',
              background: 'var(--danger-bg)', color: 'var(--danger-text)',
              borderRadius: 'var(--r-md)', fontSize: 12, textAlign: 'left',
              maxWidth: 600, overflow: 'auto', whiteSpace: 'pre-wrap',
            }}>{this.state.error.stack}</pre>
          )}
        </div>
      )
    }

    return this.props.children
  }
}
