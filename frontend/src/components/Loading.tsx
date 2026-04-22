interface Props {
  fullscreen?: boolean
  size?: 'sm' | 'md' | 'lg'
  text?: string
}

export default function Loading({ fullscreen = false, size = 'md', text }: Props) {
  const sizes = { sm: 16, md: 24, lg: 40 }
  const px = sizes[size]

  const content = (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12,
    }}>
      <div style={{
        width: px, height: px,
        border: `${size === 'sm' ? 2 : 3}px solid var(--border)`,
        borderTopColor: 'var(--brand)',
        borderRadius: '50%',
        animation: 'spin 0.6s linear infinite',
      }} />
      {text && <span style={{ fontSize: 14, color: 'var(--text-3)' }}>{text}</span>}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  )

  if (fullscreen) {
    return (
      <div style={{
        minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {content}
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: '48px 0' }}>
      {content}
    </div>
  )
}
