import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'
import Layout from './components/Layout'
import SellerLayout from './components/SellerLayout'
import AdminLayout from './components/AdminLayout'
import Loading from './components/Loading'

// 즉시 로드 (비로그인 첫 화면)
import EventList         from './pages/EventList'
import EventDetail       from './pages/EventDetail'
import Login             from './pages/Login'
import Signup            from './pages/Signup'
import NotFound          from './pages/NotFound'

// lazy – 로그인 후 접근
const SignupComplete      = lazy(() => import('./pages/SignupComplete'))
const Cart                = lazy(() => import('./pages/Cart'))
const Payment             = lazy(() => import('./pages/Payment'))
const PaymentComplete     = lazy(() => import('./pages/PaymentComplete'))
const MyPage              = lazy(() => import('./pages/MyPage'))
const SellerApply         = lazy(() => import('./pages/SellerApply'))
const PaymentSuccess      = lazy(() => import('./pages/PaymentSuccess'))
const PaymentFail         = lazy(() => import('./pages/PaymentFail'))
const WalletChargeSuccess = lazy(() => import('./pages/WalletChargeSuccess'))
const WalletChargeFail    = lazy(() => import('./pages/WalletChargeFail'))

// lazy – 판매자
const SellerDashboard   = lazy(() => import('./pages/seller/SellerDashboard'))
const SellerEventCreate = lazy(() => import('./pages/seller/SellerEventCreate'))
const SellerEventEdit   = lazy(() => import('./pages/seller/SellerEventEdit'))
const SellerEventDetail = lazy(() => import('./pages/seller/SellerEventDetail'))
const SellerSettlement  = lazy(() => import('./pages/seller/SellerSettlement'))

// lazy – 관리자
const AdminDashboard    = lazy(() => import('./pages/admin/AdminDashboard'))
const AdminUsers        = lazy(() => import('./pages/admin/AdminUsers'))
const AdminEvents       = lazy(() => import('./pages/admin/AdminEvents'))
const AdminApplications = lazy(() => import('./pages/admin/AdminApplications'))
const AdminSettlements  = lazy(() => import('./pages/admin/AdminSettlements'))

// ── 가드 ──────────────────────────────────────────────────────────
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, isLoading } = useAuth()
  if (isLoading) return <Loading fullscreen />
  if (!isLoggedIn) return <Navigate to="/login" replace />
  return <>{children}</>
}

function RequireSeller({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth()
  if (isLoading) return <Loading fullscreen />
  if (role !== 'SELLER' && role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth()
  if (isLoading) return <Loading fullscreen />
  if (role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

// ── 앱 ────────────────────────────────────────────────────────────
export default function App() {
  return (
    <Suspense fallback={<Loading fullscreen />}>
      <Routes>
        {/* 공개 */}
        <Route path="/login"               element={<Login />} />
        <Route path="/signup"              element={<Signup />} />
        <Route path="/signup/complete"     element={<SignupComplete />} />

        {/* 일반 사용자 */}
        <Route element={<Layout />}>
          <Route path="/"                       element={<EventList />} />
          <Route path="/events/:id"             element={<EventDetail />} />
          <Route path="/cart"                   element={<RequireAuth><Cart /></RequireAuth>} />
          <Route path="/payment"                element={<RequireAuth><Payment /></RequireAuth>} />
          <Route path="/payment/complete"       element={<RequireAuth><PaymentComplete /></RequireAuth>} />
          <Route path="/mypage"                 element={<RequireAuth><MyPage /></RequireAuth>} />
          <Route path="/seller-apply"           element={<RequireAuth><SellerApply /></RequireAuth>} />
          <Route path="/payment/success"        element={<RequireAuth><PaymentSuccess /></RequireAuth>} />
          <Route path="/payment/fail"           element={<RequireAuth><PaymentFail /></RequireAuth>} />
          <Route path="/wallet/charge/success"  element={<RequireAuth><WalletChargeSuccess /></RequireAuth>} />
          <Route path="/wallet/charge/fail"     element={<RequireAuth><WalletChargeFail /></RequireAuth>} />
        </Route>

        {/* 판매자 */}
        <Route element={<RequireSeller><SellerLayout /></RequireSeller>}>
          <Route path="/seller"                 element={<SellerDashboard />} />
          <Route path="/seller/events/create"   element={<SellerEventCreate />} />
          <Route path="/seller/events/:id/edit" element={<SellerEventEdit />} />
          <Route path="/seller/events/:id"      element={<SellerEventDetail />} />
          <Route path="/seller/settlements"     element={<SellerSettlement />} />
        </Route>

        {/* 관리자 */}
        <Route element={<RequireAdmin><AdminLayout /></RequireAdmin>}>
          <Route path="/admin"              element={<AdminDashboard />} />
          <Route path="/admin/users"        element={<AdminUsers />} />
          <Route path="/admin/events"       element={<AdminEvents />} />
          <Route path="/admin/applications" element={<AdminApplications />} />
          <Route path="/admin/settlements"  element={<AdminSettlements />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
