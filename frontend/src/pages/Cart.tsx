import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getCart, updateCartItemQuantity, deleteCartItem, clearCart } from '../api/cart.api'
import type { CartItemDetail } from '../api/types'
import { useToast } from '../contexts/ToastContext'
import { createOrder } from '../api/orders.api'
import PaymentModal from '../components/PaymentModal'

export default function Cart() {
  const { toast } = useToast()
  const navigate = useNavigate()
  const [items, setItems] = useState<CartItemDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [ordering, setOrdering] = useState(false)
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [orderId, setOrderId] = useState<string | null>(null)

  const fetchCart = async () => {
    try {
      const res = await getCart()
      setItems(res.data.items ?? [])
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchCart() }, [])

  const handleClear = async () => {
    if (!confirm('장바구니를 모두 비울까요?')) return
    try {
      await clearCart()
      setItems([])
    } catch { toast('초기화 실패', 'error') }
  }

  const handleCheckout = async () => {
    setOrdering(true)
    try {
      const res = await createOrder({ cartItemIds: items.map(i => i.cartItemId) })
      setOrderId(res.data.orderId)
      setPaymentOpen(true)
    } catch {
      toast('주문 생성에 실패했습니다.', 'error')
    } finally {
      setOrdering(false)
    }
  }

  const totalPrice = items.reduce((acc, i) => acc + i.price * i.quantity, 0)

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 80 }}>
      <div className="spinner" />
    </div>
  )

  return (
    <>
      <div className="container" style={{ paddingTop: 40, paddingBottom: 80, maxWidth: 840 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>장바구니</h1>
          {items.length > 0 && (
            <button className="btn btn-ghost btn-sm" onClick={handleClear}>전체 삭제</button>
          )}
        </div>

        {items.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🛒</div>
            <div className="empty-title">장바구니가 비어있습니다</div>
            <div className="empty-desc">관심 있는 이벤트를 담아보세요</div>
            <Link to="/" className="btn btn-primary">이벤트 보러가기</Link>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 24, alignItems: 'start' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {items.map(item => (
                <div key={item.eventId} className="card" style={{ padding: '16px 20px', display: 'flex', gap: 16, alignItems: 'center' }}>
                  <div style={{
                    width: 64, height: 64, borderRadius: 'var(--r-md)',
                    background: 'var(--brand-light)', flexShrink: 0,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 22, color: 'var(--brand)',
                  }}>🎫</div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="truncate" style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>
                      {item.eventTitle}
                    </div>
                    <div style={{ fontSize: 14, color: 'var(--text-3)' }}>
                      {item.price.toLocaleString()}원 / 1장
                    </div>
                  </div>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{item.quantity}매</span>
                  <div style={{ fontSize: 15, fontWeight: 700, minWidth: 80, textAlign: 'right' }}>
                    {(item.price * item.quantity).toLocaleString()}원
                  </div>
                </div>
              ))}
            </div>

            <div className="card" style={{ padding: '20px', position: 'sticky', top: 'calc(var(--nav-h) + 20px)' }}>
              <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 16 }}>주문 요약</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
                {items.map(item => (
                  <div key={item.eventId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--text-2)' }}>
                    <span className="truncate" style={{ maxWidth: 140 }}>{item.eventTitle}</span>
                    <span>{(item.price * item.quantity).toLocaleString()}원</span>
                  </div>
                ))}
              </div>
              <div style={{
                borderTop: '1px solid var(--border)', paddingTop: 12,
                display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16,
              }}>
                <span style={{ fontSize: 14, fontWeight: 500 }}>합계</span>
                <span style={{ fontSize: 20, fontWeight: 700 }}>{totalPrice.toLocaleString()}원</span>
              </div>
              <button
                className="btn btn-primary btn-full btn-lg"
                onClick={handleCheckout}
                disabled={ordering}
              >
                {ordering ? '처리 중...' : '결제하기'}
              </button>
            </div>
          </div>
        )}
      </div>

      {orderId && (
        <PaymentModal
          open={paymentOpen}
          orderId={orderId}
          totalAmount={totalPrice}
          onClose={() => setPaymentOpen(false)}
          onSuccess={() => {
            setPaymentOpen(false)
            navigate('/payment/complete', { state: { orderId, amount: totalPrice } })
          }}
        />
      )}
    </>
  )
}