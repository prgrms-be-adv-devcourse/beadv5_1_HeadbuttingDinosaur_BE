import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useToast } from '../contexts/ToastContext'
import { getTickets } from '../api/tickets.api'
import TicketDetailModal from '../components/TicketDetailModal'
import { getOrders, cancelOrder } from '../api/orders.api'
import { getWalletBalance, getWalletTransactions, startWalletCharge, withdrawWallet } from '../api/wallet.api'
import { getRefunds } from '../api/refunds.api'
import { updateProfile, changePassword, withdrawUser } from '../api/auth.api'
import { refundByWallet } from '../api/refunds.api'
import type {
  TicketItem, OrderItem, WalletTransactionItem, RefundItem,
  UpdateProfileRequest,
} from '../api/types'

type Tab = 'tickets' | 'orders' | 'wallet' | 'refunds' | 'settings'

const TABS: { key: Tab; label: string; icon: string }[] = [
  { key: 'tickets',  label: '내 티켓',   icon: '🎫' },
  { key: 'orders',   label: '주문 내역', icon: '📦' },
  { key: 'wallet',   label: '예치금',    icon: '💰' },
  { key: 'refunds',  label: '환불 내역', icon: '↩️' },
  { key: 'settings', label: '설정',      icon: '⚙️' },
]

const TICKET_STATUS: Record<string, { label: string; cls: string }> = {
  VALID:    { label: '사용 가능', cls: 'badge-green' },
  USED:     { label: '사용 완료', cls: 'badge-gray' },
  CANCELLED:{ label: '취소됨',   cls: 'badge-red' },
  EXPIRED:  { label: '만료',     cls: 'badge-gray' },
}

const ORDER_STATUS: Record<string, { label: string; cls: string }> = {
  CREATED:         { label: '주문 생성',   cls: 'badge-amber' },
  PAYMENT_PENDING: { label: '결제 대기',   cls: 'badge-amber' },
  PAID:            { label: '결제 완료',   cls: 'badge-green' },
  CANCELLED:       { label: '취소됨',     cls: 'badge-gray' },
  REFUNDED:        { label: '환불 완료',   cls: 'badge-blue' },
}

const TX_TYPE: Record<string, { label: string; sign: string; color: string }> = {
  CHARGE:  { label: '충전',   sign: '+', color: 'var(--success)' },
  USE:     { label: '사용',   sign: '-', color: 'var(--danger)' },
  REFUND:  { label: '환불',   sign: '+', color: 'var(--info)' },
  WITHDRAW:{ label: '출금',   sign: '-', color: 'var(--text-3)' },
}

export default function MyPage() {
  const { user, refresh } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = (searchParams.get('tab') as Tab) || 'tickets'

  const setTab = (t: Tab) => setSearchParams({ tab: t })

  return (
    <div className="container" style={{ paddingTop: 40, paddingBottom: 80, maxWidth: 860 }}>
      {/* Profile header */}
      <div className="card" style={{ padding: '24px 28px', marginBottom: 24, display: 'flex', alignItems: 'center', gap: 20 }}>
        <div style={{
          width: 64, height: 64, borderRadius: '50%',
          background: 'var(--brand-light)', color: 'var(--brand)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 24, fontWeight: 700, flexShrink: 0,
        }}>{user?.nickname?.charAt(0).toUpperCase()}</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--text)' }}>{user?.nickname}</div>
          <div style={{ fontSize: 14, color: 'var(--text-3)', marginTop: 2 }}>{user?.email}</div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            {user?.role && (
              <span className={`badge ${user.role === 'SELLER' ? 'badge-brand' : user.role === 'ADMIN' ? 'badge-blue' : 'badge-gray'}`}>
                {user.role === 'USER' ? '일반 회원' : user.role === 'SELLER' ? '판매자' : '관리자'}
              </span>
            )}
            {user?.position && <span className="badge badge-gray">{user.position}</span>}
          </div>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => setTab('settings')}>프로필 수정</button>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, marginBottom: 24, borderBottom: '1px solid var(--border)', paddingBottom: 0 }}>
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)} style={{
            padding: '10px 16px', fontSize: 14, fontWeight: tab === t.key ? 600 : 400,
            color: tab === t.key ? 'var(--brand)' : 'var(--text-3)',
            background: 'none', border: 'none',
            borderBottom: `2px solid ${tab === t.key ? 'var(--brand)' : 'transparent'}`,
            cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, transition: 'all 0.15s',
            marginBottom: -1,
          }}>
            <span style={{ fontSize: 14 }}>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {tab === 'tickets'  && <TicketsTab toast={toast} />}
      {tab === 'orders'   && <OrdersTab toast={toast} />}
      {tab === 'wallet'   && <WalletTab toast={toast} />}
      {tab === 'refunds'  && <RefundsTab toast={toast} />}
      {tab === 'settings' && <SettingsTab user={user} toast={toast} refresh={refresh} navigate={navigate} />}
    </div>
  )
}

// ── Tickets Tab ───────────────────────────────────────────────────
function TicketsTab({ toast }: { toast: any }) {
  const [tickets, setTickets] = useState<TicketItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    getTickets({ page: 0, size: 20 })
      .then(r => setTickets(r.data.tickets))
      .catch(() => toast('로드 실패', 'error'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <LoadingSpinner />
  if (tickets.length === 0) return (
    <EmptyState icon="🎫" title="보유한 티켓이 없습니다" desc="이벤트를 구매하면 여기에 표시됩니다" />
  )

  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 14 }}>
        {tickets.map(ticket => {
          const status = TICKET_STATUS[ticket.status] ?? { label: ticket.status, cls: 'badge-gray' }
          return (
            <div key={ticket.ticketId}  className="card" style={{ padding: '18px 20px', cursor: 'pointer', transition: 'box-shadow 0.15s' }}
              onClick={() =>{
                  console.log("---")
                 setSelectedId(ticket.ticketId)
              }}
              onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-md)'}
              onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-sm)'}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                <span style={{ fontSize: 22 }}>🎫</span>
                <span className={`badge ${status.cls}`}>{status.label}</span>
              </div>
              <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 6, lineHeight: 1.4 }}>{ticket.eventTitle}</div>
              <div style={{ fontSize: 13, color: 'var(--text-3)' }}>
                {new Date(ticket.eventDate).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
              </div>
              <div style={{ marginTop: 10, fontSize: 11, color: 'var(--text-4)', fontFamily: 'var(--font-mono)' }}>
                #{ticket.ticketId}
              </div>
            </div>
          )
        })}
      </div>

      <TicketDetailModal ticketId={selectedId} onClose={() => setSelectedId(null)} />
    </>
  )
}

// ── Orders Tab ────────────────────────────────────────────────────
function OrdersTab({ toast }: { toast: any }) {
  const [orders, setOrders] = useState<OrderItem[]>([])
  const [loading, setLoading] = useState(true)

  const fetchOrders = () => {
    getOrders({ page: 0, size: 20 }).then(r => setOrders(r.data.orders)).catch(() => toast('로드 실패', 'error')).finally(() => setLoading(false))
  }

  useEffect(() => { fetchOrders() }, [])

  const handleCancel = async (orderId: string) => {
    if (!confirm('주문을 취소할까요?')) return
    try {
      await cancelOrder(orderId)
      toast('주문이 취소되었습니다', 'success')
      fetchOrders()
    } catch { toast('취소 실패', 'error') }
  }

  const handleRefund = async (orderId: string) => {
    if (!confirm('환불을 요청할까요?')) return
    try {
      await refundByWallet({ orderId })
      toast('환불 요청이 완료되었습니다', 'success')
      fetchOrders()
    } catch { toast('환불 요청 실패', 'error') }
  }

  if (loading) return <LoadingSpinner />
  if (orders.length === 0) return <EmptyState icon="📦" title="주문 내역이 없습니다" desc="이벤트 티켓을 구매해보세요" />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {orders.map(order => {
        const status = ORDER_STATUS[order.status] ?? { label: order.status, cls: 'badge-gray' }
        const canCancel = ['CREATED', 'PAYMENT_PENDING'].includes(order.status)
        const canRefund = order.status === 'PAID'
        return (
          <div key={order.orderId} className="card" style={{ padding: '18px 20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
              <div>
                <span className={`badge ${status.cls}`} style={{ marginBottom: 6, display: 'inline-block' }}>{status.label}</span>
                <div style={{ fontSize: 11, color: 'var(--text-4)', fontFamily: 'var(--font-mono)' }}>#{order.orderId.slice(0, 16)}</div>
              </div>
              <div style={{ fontSize: 18, fontWeight: 700 }}>{order.totalAmount.toLocaleString()}원</div>
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-3)', marginBottom: 12 }}>
              {new Date(order.createdAt).toLocaleString('ko-KR')}
            </div>
            {(canCancel || canRefund) && (
              <div style={{ display: 'flex', gap: 8, borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                {canCancel && (
                  <button className="btn btn-ghost btn-sm" onClick={() => handleCancel(order.orderId)}>주문 취소</button>
                )}
                {canRefund && (
                  <button className="btn btn-danger btn-sm" onClick={() => handleRefund(order.orderId)}>환불 요청</button>
                )}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ── Wallet Tab ────────────────────────────────────────────────────
function WalletTab({ toast }: { toast: any }) {
  const [balance, setBalance] = useState<number | null>(null)
  const [transactions, setTransactions] = useState<WalletTransactionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [chargeAmount, setChargeAmount] = useState('')
  const [withdrawAmount, setWithdrawAmount] = useState('')
  const [mode, setMode] = useState<null | 'charge' | 'withdraw'>(null)
  const [processing, setProcessing] = useState(false)

  const fetchWallet = async () => {
    try {
      const [bal, tx] = await Promise.all([
        getWalletBalance(),
        getWalletTransactions({ page: 0, size: 20 }),
      ])
      setBalance(bal.data.balance)
      setTransactions(tx.data.items)
    } catch { toast('로드 실패', 'error') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchWallet() }, [])

  const handleCharge = async () => {
    const amount = parseInt(chargeAmount)
    if (!amount || amount < 1000) { toast('최소 1,000원 이상 충전 가능합니다', 'error'); return }
    setProcessing(true)

    let transactionId: string | undefined
    let chargedAmount: number | undefined

    try {
      // 1단계: 충전 요청
      const res = await startWalletCharge({ amount })
      const data = res.data
      transactionId = data.chargeId
      chargedAmount = data.amount
    } catch {
      toast('충전 요청 실패. 잠시 후 다시 시도해주세요.', 'error')
      setProcessing(false)
      return
    }



    try {
      // 2단계: Toss 결제창 오픈
      sessionStorage.setItem('wallet_charge_context', JSON.stringify({ transactionId, amount: chargedAmount }))

      const tossPayments = window.TossPayments("test_ck_GjLJoQ1aVZplbR1KB0MW8w6KYe2R")

      await tossPayments.requestPayment('카드', {
        amount: chargedAmount!,
        orderId: transactionId!,
        orderName: '예치금 충전',
        successUrl: `${window.location.origin}/wallet/charge/success`,
        failUrl: `${window.location.origin}/wallet/charge/fail`,
      })
      // requestPayment는 성공 시 successUrl로 리다이렉트되므로 이 아래는 실행되지 않음
    } catch (e: any) {
      const msg = e?.message ?? '결제창 실행에 실패했습니다.'
      toast(msg, 'error')
      sessionStorage.removeItem('wallet_charge_context')
      setProcessing(false)
    }
  }

  const handleWithdraw = async () => {
    const amount = parseInt(withdrawAmount)
    if (!amount || amount < 1000) { toast('최소 1,000원 이상 출금 가능합니다', 'error'); return }
    if (balance !== null && amount > balance) { toast('잔액이 부족합니다', 'error'); return }
    setProcessing(true)
    try {
      await withdrawWallet({ amount })
      toast('출금 요청이 완료되었습니다', 'success')
      setMode(null)
      setWithdrawAmount('')
      fetchWallet()
    } catch { toast('출금 실패', 'error') }
    finally { setProcessing(false) }
  }

  const QUICK_AMOUNTS = [10000, 30000, 50000]

  if (loading) return <LoadingSpinner />

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Balance card */}
      <div className="card" style={{ padding: '28px 32px', background: 'linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%)', border: 'none', color: '#fff' }}>
        <div style={{ fontSize: 13, opacity: 0.8, marginBottom: 8 }}>보유 예치금</div>
        <div style={{ fontSize: 36, fontWeight: 700, marginBottom: 20 }}>
          {balance !== null ? balance.toLocaleString() : '—'}원
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setMode(mode === 'charge' ? null : 'charge')} style={{
            padding: '8px 20px', borderRadius: 'var(--r-md)', fontSize: 14, fontWeight: 500,
            background: 'rgba(255,255,255,0.2)', color: '#fff', border: '1px solid rgba(255,255,255,0.3)',
            cursor: 'pointer', backdropFilter: 'blur(8px)',
          }}>충전</button>
          <button onClick={() => setMode(mode === 'withdraw' ? null : 'withdraw')} style={{
            padding: '8px 20px', borderRadius: 'var(--r-md)', fontSize: 14, fontWeight: 500,
            background: 'rgba(255,255,255,0.1)', color: '#fff', border: '1px solid rgba(255,255,255,0.2)',
            cursor: 'pointer',
          }}>출금</button>
        </div>
      </div>

      {/* Charge panel */}
      {mode === 'charge' && (
        <div className="card" style={{ padding: '20px 24px' }}>
          <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 14 }}>충전 금액</div>
          <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
            {QUICK_AMOUNTS.map(a => (
              <button key={a} onClick={() => setChargeAmount(String(a))} style={{
                padding: '6px 14px', borderRadius: 'var(--r-md)', fontSize: 13, cursor: 'pointer',
                border: `1px solid ${chargeAmount === String(a) ? 'var(--brand)' : 'var(--border)'}`,
                background: chargeAmount === String(a) ? 'var(--brand-light)' : 'var(--surface)',
                color: chargeAmount === String(a) ? 'var(--brand)' : 'var(--text-2)',
              }}>{a.toLocaleString()}원</button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input className="form-input" type="number" placeholder="직접 입력 (원)" value={chargeAmount}
              onChange={e => setChargeAmount(e.target.value)} style={{ flex: 1 }} />
            <button className="btn btn-primary" onClick={handleCharge} disabled={processing}>
              {processing ? '처리 중...' : '충전하기'}
            </button>
          </div>
        </div>
      )}

      {/* Withdraw panel */}
      {mode === 'withdraw' && (
        <div className="card" style={{ padding: '20px 24px' }}>
          <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 14 }}>출금 금액</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <input className="form-input" type="number" placeholder="출금 금액 (원)" value={withdrawAmount}
              onChange={e => setWithdrawAmount(e.target.value)} style={{ flex: 1 }} />
            <button className="btn btn-secondary" onClick={handleWithdraw} disabled={processing}>
              {processing ? '처리 중...' : '출금하기'}
            </button>
          </div>
          {balance !== null && (
            <button style={{ marginTop: 8, fontSize: 12, color: 'var(--text-3)', background: 'none', border: 'none', cursor: 'pointer' }}
              onClick={() => setWithdrawAmount(String(balance))}>전액 출금 ({balance.toLocaleString()}원)</button>
          )}
        </div>
      )}

      {/* Transactions */}
      <div className="card" style={{ overflow: 'hidden' }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)', fontSize: 15, fontWeight: 600 }}>거래 내역</div>
        {transactions.length === 0 ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-3)', fontSize: 14 }}>거래 내역이 없습니다</div>
        ) : (
          <div>
            {transactions.map(tx => {
              const type = TX_TYPE[tx.type] ?? { label: tx.type, sign: '', color: 'var(--text)' }
              return (
                <div key={tx.transactionId} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '14px 20px', borderBottom: '1px solid var(--border)',
                }}>
                  <div>
                    <span className="badge badge-gray" style={{ marginBottom: 4 }}>{type.label}</span>
                    <div style={{ fontSize: 11, color: 'var(--text-4)' }}>
                      {new Date(tx.createdAt).toLocaleDateString('ko-KR')}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 16, fontWeight: 700, color: type.color }}>
                      {type.sign}{tx.amount.toLocaleString()}원
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-4)' }}>
                      잔액 {tx.balanceAfter.toLocaleString()}원
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

// ── Refunds Tab ───────────────────────────────────────────────────
function RefundsTab({ toast }: { toast: any }) {
  const [refunds, setRefunds] = useState<RefundItem[]>([])
  const [loading, setLoading] = useState(true)

  const REFUND_STATUS: Record<string, { label: string; cls: string }> = {
    REQUESTED:   { label: '처리 중', cls: 'badge-amber' },
    APPROVED: { label: '처리 중', cls: 'badge-amber' },
    COMPLETED: { label: '완료',    cls: 'badge-green' },
    REJECTED:  { label: '거절됨',  cls: 'badge-red' },
    FAILED: { label: '취소됨',  cls: 'badge-gray' },
  }

  useEffect(() => {
    getRefunds().then(r => setRefunds(r.data.content)).catch(() => toast('로드 실패', 'error')).finally(() => setLoading(false))
  }, [])

  if (loading) return <LoadingSpinner />
  if (refunds.length === 0) return <EmptyState icon="↩️" title="환불 내역이 없습니다" />

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <table>
        <thead>
          <tr>
            <th>환불 ID</th>
            <th>주문 ID</th>
            <th>환불 금액</th>
            <th>상태</th>
            <th>요청일</th>
          </tr>
        </thead>
        <tbody>
          {refunds.map(r => {
            const status = REFUND_STATUS[r.status] ?? { label: r.status, cls: 'badge-gray' }
            return (
              <tr key={r.refundId}>
                <td style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{r.refundId.slice(0, 12)}...</td>
                <td style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{r.orderId.slice(0, 12)}...</td>
                <td style={{ fontWeight: 600 }}>{r.refundAmount.toLocaleString()}원</td>
                <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                <td style={{ color: 'var(--text-3)', fontSize: 13 }}>{new Date(r.requestedAt).toLocaleDateString('ko-KR')}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

// ── Settings Tab ──────────────────────────────────────────────────
function SettingsTab({ user, toast, refresh, navigate }: { user: any; toast: any; refresh: any; navigate: any }) {
  const [profile, setProfile] = useState<UpdateProfileRequest>({
    nickname: user?.nickname ?? '',
    position: user?.position ?? '',
  })
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPw, setSavingPw] = useState(false)

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    setSavingProfile(true)
    try {
      await updateProfile(profile)
      await refresh()
      toast('프로필이 수정되었습니다', 'success')
    } catch { toast('수정 실패', 'error') }
    finally { setSavingProfile(false) }
  }

  const handleChangePw = async (e: React.FormEvent) => {
    e.preventDefault()
    if (pw.newPassword !== pw.confirmPassword) { toast('새 비밀번호가 일치하지 않습니다', 'error'); return }
    if (pw.newPassword.length < 8) { toast('새 비밀번호는 8자 이상이어야 합니다', 'error'); return }
    setSavingPw(true)
    try {
      await changePassword({ currentPassword: pw.currentPassword, newPassword: pw.newPassword, newPasswordConfirm : pw.confirmPassword})
      toast('비밀번호가 변경되었습니다', 'success')
      setPw({ currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch { toast('비밀번호 변경 실패. 현재 비밀번호를 확인하세요.', 'error') }
    finally { setSavingPw(false) }
  }

  const handleWithdraw = async () => {
    if (!confirm('정말 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return
    try {
      await withdrawUser()
      toast('탈퇴되었습니다', 'success')
      navigate('/login')
    } catch { toast('탈퇴 실패', 'error') }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Profile */}
      <div className="card" style={{ padding: '24px' }}>
        <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 20 }}>프로필 수정</h2>
        <form onSubmit={handleSaveProfile} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div className="form-group">
            <label className="form-label">닉네임</label>
            <input className="form-input" value={profile.nickname ?? ''} onChange={e => setProfile(p => ({ ...p, nickname: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">포지션</label>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {[
                { value: 'BACKEND', label: '백엔드' },
                { value: 'FRONTEND', label: '프론트엔드' },
                { value: 'FULLSTACK', label: '풀스택' },
                { value: 'DEVOPS', label: '데브옵스' },
                { value: 'AI_ML', label: 'AI/ML' },
                { value: 'MOBILE', label: '모바일' },
                { value: 'OTHER', label: '기타' },
              ].map(pos => (
                  <button
                      key={pos.value}
                      type="button"
                      onClick={() => setProfile(p => ({ ...p, position: pos.value }))}
                      style={{
                        padding: '7px 16px', borderRadius: '999px', fontSize: 13, cursor: 'pointer',
                        border: `1.5px solid ${profile.position === pos.value ? 'var(--brand)' : 'var(--border)'}`,
                        background: profile.position === pos.value ? 'var(--brand)' : 'var(--surface)',
                        color: profile.position === pos.value ? '#fff' : 'var(--text-2)',
                        fontWeight: profile.position === pos.value ? 600 : 400,
                        transition: 'all 0.15s',
                      }}
                  >{pos.label}</button>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button type="submit" className="btn btn-primary" disabled={savingProfile}>
              {savingProfile ? '저장 중...' : '저장'}
            </button>
          </div>
        </form>
      </div>

      {/* Password */}
      {user?.providerType === 'LOCAL' && (
        <div className="card" style={{ padding: '24px' }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 20 }}>비밀번호 변경</h2>
          <form onSubmit={handleChangePw} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">현재 비밀번호</label>
              <input className="form-input" type="password" value={pw.currentPassword} onChange={e => setPw(p => ({ ...p, currentPassword: e.target.value }))} />
            </div>
            <div className="form-group">
              <label className="form-label">새 비밀번호</label>
              <input className="form-input" type="password" value={pw.newPassword} onChange={e => setPw(p => ({ ...p, newPassword: e.target.value }))} />
            </div>
            <div className="form-group">
              <label className="form-label">새 비밀번호 확인</label>
              <input className="form-input" type="password" value={pw.confirmPassword} onChange={e => setPw(p => ({ ...p, confirmPassword: e.target.value }))} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button type="submit" className="btn btn-secondary" disabled={savingPw}>
                {savingPw ? '변경 중...' : '비밀번호 변경'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Seller apply */}
      {user?.role === 'USER' && (
        <div className="card" style={{ padding: '20px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>판매자 전환 신청</div>
            <div style={{ fontSize: 13, color: 'var(--text-3)' }}>이벤트를 직접 등록하고 관리할 수 있습니다</div>
          </div>
          <a href="/seller-apply" className="btn btn-secondary">신청하기</a>
        </div>
      )}

      {/* Danger zone */}
      <div className="card" style={{ padding: '20px 24px', borderColor: '#FECACA' }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--danger)', marginBottom: 4 }}>위험 구역</div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, color: 'var(--text-3)' }}>계정을 삭제하면 모든 데이터가 사라집니다</div>
          <button className="btn btn-danger btn-sm" onClick={handleWithdraw}>회원 탈퇴</button>
        </div>
      </div>
    </div>
  )
}

// ── Shared helpers ────────────────────────────────────────────────
function LoadingSpinner() {
  return <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
}

function EmptyState({ icon, title, desc }: { icon: string; title: string; desc?: string }) {
  return (
    <div className="empty-state">
      <div className="empty-icon">{icon}</div>
      <div className="empty-title">{title}</div>
      {desc && <div className="empty-desc">{desc}</div>}
    </div>
  )
}
