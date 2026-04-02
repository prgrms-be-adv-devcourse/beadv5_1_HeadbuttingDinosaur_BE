interface Props {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

export default function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null

  const pages: (number | '...')[] = []
  const delta = 2
  const left = Math.max(0, page - delta)
  const right = Math.min(totalPages - 1, page + delta)

  if (left > 0) { pages.push(0); if (left > 1) pages.push('...') }
  for (let i = left; i <= right; i++) pages.push(i)
  if (right < totalPages - 1) { if (right < totalPages - 2) pages.push('...'); pages.push(totalPages - 1) }

  return (
    <div className="pagination" style={{ justifyContent: 'center', marginTop: 32 }}>
      <button
        className="page-btn"
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
      >‹</button>

      {pages.map((p, i) =>
        p === '...' ? (
          <span key={`dots-${i}`} style={{ width: 34, textAlign: 'center', color: 'var(--text-4)' }}>…</span>
        ) : (
          <button
            key={p}
            className={`page-btn${p === page ? ' active' : ''}`}
            onClick={() => onChange(p as number)}
          >{(p as number) + 1}</button>
        )
      )}

      <button
        className="page-btn"
        onClick={() => onChange(page + 1)}
        disabled={page === totalPages - 1}
      >›</button>
    </div>
  )
}
