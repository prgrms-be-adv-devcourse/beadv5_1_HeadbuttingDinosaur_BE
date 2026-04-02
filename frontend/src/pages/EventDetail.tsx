import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getEventDetail } from '../api/events.api'
import { addCartItem } from '../api/cart.api'
import type { EventDetailResponse } from '../api/types'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'

const STATUS_MAP: Record<string, { label: string; color: string; bg: string }> = {
  ON_SALE:  { label: '판매 중',  color: 'var(--success-text)', bg: 'var(--success-bg)' },
  SOLD_OUT: { label: '매진',     color: 'var(--danger-text)',  bg: 'var(--danger-bg)' },
  ENDED:    { label: '종료',     color: 'var(--text-3)',       bg: 'var(--surface-2)' },
  CANCELLED:{ label: '취소됨',   color: 'var(--text-3)',       bg: 'var(--surface-2)' },
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
    weekday: 'short', hour: '2-digit', minute: '2-digit',
  })
}

const PLACEHOLDER_COLORS = ['#4F46E5','#0EA5E9','#10B981','#F59E0B','#8B5CF6']

export default function EventDetail() {
  const { id } = useParams<{ id: string }>()
  const { isLoggedIn } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [event, setEvent] = useState<EventDetailResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [quantity, setQuantity] = useState(1)
  const [addingCart, setAddingCart] = useState(false)
  console.log("event:", event);

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getEventDetail(id).then(res => {
      setEvent(res.data.data)
    }).catch(() => {
      toast('이벤트를 불러오지 못했습니다.', 'error')
    }).finally(() => setLoading(false))
  }, [id])

  const handleAddCart = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    if (!event) return
    setAddingCart(true)
    try {
      await addCartItem({ eventId: event.eventId, quantity })
      toast('장바구니에 담았습니다 🛒', 'success')
    } catch {
      toast('장바구니 담기 실패. 다시 시도해주세요.', 'error')
    } finally {
      setAddingCart(false)
    }
  }

  const handleBuyNow = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    if (!event) return
    setAddingCart(true)
    try {
      await addCartItem({ eventId: event.eventId, quantity })
      navigate('/cart')
    } catch {
      toast('오류가 발생했습니다.', 'error')
      setAddingCart(false)
    }
  }

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '50vh' }}>
      <div className="spinner" />
    </div>
  )

  if (!event) return (
    <div className="empty-state" style={{ minHeight: '50vh' }}>
      <div className="empty-icon">😕</div>
      <div className="empty-title">이벤트를 찾을 수 없습니다</div>
      <button className="btn btn-primary" onClick={() => navigate('/')}>목록으로</button>
    </div>
  )

  const status = STATUS_MAP[event.status] ?? STATUS_MAP.ENDED
  const color = PLACEHOLDER_COLORS[event.eventId.charCodeAt(0) % PLACEHOLDER_COLORS.length]
  const canBuy = event.status === 'ON_SALE' && event.remainingQuantity > 0
  const totalPrice = event.price * quantity

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '40px 24px 80px' }}>
      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24, fontSize: 13, color: 'var(--text-3)' }}>
        <button onClick={() => navigate('/')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit' }}>이벤트</button>
        <span>›</span>
        <span style={{ color: 'var(--text-2)' }} className="truncate">{event.title}</span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 32, alignItems: 'start' }}>
        {/* Left */}
        <div>
          {/* Thumbnail */}
          <div style={{
            height: 320, borderRadius: 'var(--r-xl)', overflow: 'hidden', marginBottom: 28,
            background: event.thumbnailUrl
              ? `url(${event.thumbnailUrl}) center/cover`
              : `linear-gradient(135deg, ${color}15 0%, ${color}35 100%)`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            border: '1px solid var(--border)',
          }}>
            {!event.thumbnailUrl && (
              <span style={{ fontSize: 64, opacity: 0.3, fontFamily: 'var(--font-mono)', color }}>&#x276F;_</span>
            )}
          </div>

          {/* Category + Status */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
            <span style={{
              fontSize: 12, fontWeight: 600, color: 'var(--brand)',
              fontFamily: 'var(--font-mono)',
            }}>{event.category}</span>
            <span style={{
              padding: '2px 8px', borderRadius: 'var(--r-full)', fontSize: 12, fontWeight: 500,
              color: status.color, background: status.bg,
            }}>{status.label}</span>
          </div>

          {/* Title */}
          <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--text)', lineHeight: 1.35, marginBottom: 16 }}>
            {event.title}
          </h1>

          {/* Tech stacks */}
          {event.techStacks?.map((t: any) => (
            <span key={typeof t === 'string' ? t : t.name} className="tag">
              {typeof t === 'string' ? t : t.name}
            </span>
          ))}

          {/* Meta info */}
          <div className="card" style={{ padding: '16px 20px', marginBottom: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <MetaRow icon="📅" label="일시" value={formatDate(event.eventDateTime)} />
            <MetaRow icon="📍" label="장소" value={event.location} />
            <MetaRow icon="👤" label="주최" value={event.sellerNickname} />
            <MetaRow icon="🎫" label="잔여 좌석" value={
              event.remainingQuantity === 0
                ? <span style={{ color: 'var(--danger)' }}>매진</span>
                : <span>{event.remainingQuantity.toLocaleString()}석</span>
            } />
          </div>

          {/* Description */}
          <div>
            <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 12, color: 'var(--text)' }}>이벤트 소개</h2>
            <div style={{
              fontSize: 15, color: 'var(--text-2)', lineHeight: 1.75,
              whiteSpace: 'pre-wrap',
            }}>
              {event.description || '상세 설명이 없습니다.'}
            </div>
          </div>
        </div>

        {/* Right – Purchase box */}
        <div style={{ position: 'sticky', top: 'calc(var(--nav-h) + 20px)' }}>
          <div className="card" style={{ padding: '24px' }}>
            {/* Price */}
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, color: 'var(--text-3)', marginBottom: 4 }}>티켓 가격</div>
              <div style={{ fontSize: 28, fontWeight: 700, color: event.price === 0 ? 'var(--success)' : 'var(--text)' }}>
                {event.price === 0 ? '무료' : `${event.price.toLocaleString()}원`}
              </div>
            </div>

            {/* Quantity */}
            {event.price > 0 && canBuy && (
              <div style={{ marginBottom: 20 }}>
                <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 8, color: 'var(--text-2)' }}>수량</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <button
                    onClick={() => setQuantity(q => Math.max(1, q - 1))}
                    style={{
                      width: 34, height: 34, borderRadius: 'var(--r-md)',
                      border: '1px solid var(--border-2)', background: 'var(--surface)',
                      fontSize: 18, cursor: 'pointer', display: 'flex',
                      alignItems: 'center', justifyContent: 'center',
                    }}
                  >−</button>
                  <span style={{ fontSize: 16, fontWeight: 600, minWidth: 24, textAlign: 'center' }}>{quantity}</span>
                  <button
                    onClick={() => setQuantity(q => Math.min(event.remainingQuantity, q + 1))}
                    style={{
                      width: 34, height: 34, borderRadius: 'var(--r-md)',
                      border: '1px solid var(--border-2)', background: 'var(--surface)',
                      fontSize: 18, cursor: 'pointer', display: 'flex',
                      alignItems: 'center', justifyContent: 'center',
                    }}
                  >+</button>
                </div>
              </div>
            )}

            {/* Total */}
            {event.price > 0 && (
              <div style={{
                padding: '12px 0', borderTop: '1px solid var(--border)',
                display: 'flex', justifyContent: 'space-between', marginBottom: 16,
              }}>
                <span style={{ fontSize: 14, color: 'var(--text-2)' }}>합계</span>
                <span style={{ fontSize: 18, fontWeight: 700 }}>{totalPrice.toLocaleString()}원</span>
              </div>
            )}

            {/* Buttons */}
            {canBuy ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button
                  className="btn btn-primary btn-full btn-lg"
                  onClick={handleBuyNow}
                  disabled={addingCart}
                >바로 구매하기</button>
                <button
                  className="btn btn-secondary btn-full"
                  onClick={handleAddCart}
                  disabled={addingCart}
                >🛒 장바구니 담기</button>
              </div>
            ) : (
              <button className="btn btn-full btn-lg" disabled style={{ background: 'var(--surface-2)', color: 'var(--text-3)', justifyContent: 'center' }}>
                {event.status === 'SOLD_OUT' ? '매진된 이벤트입니다' : '구매 불가'}
              </button>
            )}

            {/* Info */}
            <div style={{ marginTop: 16, fontSize: 12, color: 'var(--text-4)', lineHeight: 1.6 }}>
              * 결제 완료 후 즉시 티켓이 발급됩니다<br />
              * 행사 7일 전까지 100% 환불 가능
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function MetaRow({ icon, label, value }: { icon: string; label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
      <span style={{ fontSize: 16, flexShrink: 0 }}>{icon}</span>
      <div>
        <div style={{ fontSize: 11, color: 'var(--text-3)', marginBottom: 2 }}>{label}</div>
        <div style={{ fontSize: 14, color: 'var(--text)', fontWeight: 500 }}>{value}</div>
      </div>
    </div>
  )
}
