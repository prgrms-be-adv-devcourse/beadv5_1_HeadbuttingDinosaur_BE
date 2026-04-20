import { useEffect, useState } from 'react'
import Modal from './Modal'
import { getTicketDetail } from '../api/tickets.api'
import type { TicketDetailResponse } from '../api/types'

const STATUS: Record<string, { label: string; cls: string }> = {
  VALID:    { label: '사용 가능', cls: 'badge-green' },
  ISSUED:   { label: '발급 완료', cls: 'badge-blue' },
  USED:     { label: '사용 완료', cls: 'badge-gray' },
  CANCELLED:{ label: '취소됨',   cls: 'badge-red' },
  EXPIRED:  { label: '만료',     cls: 'badge-gray' },
}

interface Props {
  ticketId: number | null
  onClose: () => void
}

export default function TicketDetailModal({ ticketId, onClose }: Props) {
  const [detail, setDetail] = useState<TicketDetailResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (ticketId === null) return
    setDetail(null)
    setError(false)
    setLoading(true)
    getTicketDetail(String(ticketId))
      .then(r => setDetail((r.data as any).data ?? r.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [ticketId])

  const status = detail ? (STATUS[detail.status] ?? { label: detail.status, cls: 'badge-gray' }) : null

  return (
    <Modal open={ticketId !== null} onClose={onClose} title="티켓 상세" width={420}>
      {loading && (
        <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--text-3)', fontSize: 14 }}>
          불러오는 중...
        </div>
      )}

      {error && (
        <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--danger)', fontSize: 14 }}>
          티켓 정보를 불러오지 못했습니다.
        </div>
      )}

      {detail && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>

          {/* 상태 */}
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <span className={`badge ${status!.cls}`} style={{ fontSize: 13, padding: '4px 14px' }}>
              {status!.label}
            </span>
          </div>

          {/* 정보 목록 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Row label="이벤트" value={detail.eventTitle} bold />
            <Row label="일시" value={formatDate(detail.eventDateTime)} />
            {detail.location && <Row label="장소" value={detail.location} />}
            <Row label="발급일" value={formatDate(detail.issuedAt)} />
            <Row
              label="티켓 번호"
              value={`#${detail.ticketId}`}
              mono
            />
          </div>
        </div>
      )}
    </Modal>
  )
}

function Row({ label, value, bold, mono }: { label: string; value: string; bold?: boolean; mono?: boolean }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
      <span style={{ fontSize: 13, color: 'var(--text-3)', flexShrink: 0 }}>{label}</span>
      <span style={{
        fontSize: 13,
        color: 'var(--text)',
        fontWeight: bold ? 600 : 400,
        fontFamily: mono ? 'var(--font-mono)' : undefined,
        textAlign: 'right',
      }}>
        {value}
      </span>
    </div>
  )
}

function formatDate(str: string) {
  if (!str) return '-'
  return new Date(str).toLocaleString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}
