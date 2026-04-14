// ══════════════════════════════════════════════════════════════════
//  DevTicket – 공유 타입 정의
//  백엔드 dto_catalog_v2 + 실제 엔티티 기반
// ══════════════════════════════════════════════════════════════════

// ── Auth ────────────────────────────────────────────────────────────────────
export interface SignUpRequest {
  email: string;
  password: string;
  passwordConfirm: string;
}
export interface SignUpResponse {
  userId: string;
  accessToken: string; // 백엔드에서 가입 즉시 토큰 발급
  refreshToken: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface SocialSignUpOrLoginRequest {
  providerType: string; // "GOOGLE"
  idToken: string;
}
export interface SocialSignUpOrLoginResponse {
  userId: string;
  isNewUser: boolean;
  isProfileCompleted: boolean;
  accessToken: string;
  refreshToken: string;
}

export interface LogoutResponse {
  success: boolean;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}
export interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
}

// ── User / Profile ───────────────────────────────────────────────────────────
export interface WithdrawResponse {
  userId: string;
  withdrawnAt: string;
}

export interface SignUpProfileRequest {
  nickname: string;
  position: string;
  techStackIds: number[]; // Long[] — tech stack은 공개 마스터 데이터라 Long 사용
  profileImageUrl: string | null;
  bio: string | null;
}
export interface SignUpProfileResponse {
  profileId: string;
}

export interface GetProfileResponse {
  userId: string;
  email: string;
  nickname: string;
  role: string;
  status: string;
  providerType: string;
  position: string;
  profileImageUrl: string | null;
  bio: string | null;
  techStacks: TechStackItem[];
}

export interface UpdateProfileRequest {
  nickname?: string;
  position?: string;
  techStackIds?: number[];
  profileImageUrl?: string;
  bio?: string;
}
export interface UpdateProfileResponse {
  nickname: string;
  position: string;
  profileImageUrl: string | null;
  techStacks: TechStackItem[];
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  newPasswordConfirm: string;
}
export interface ChangePasswordResponse {
  success: boolean;
}

// ── Seller Application ───────────────────────────────────────────────────────
export interface SellerApplicationRequest {
  bankName: string;
  accountNumber: string;
}
export interface SellerApplicationListResponse {
  applicationId: string;
  userId: string;
  bankName: string;
  accountNumber: string;
  accountHolder: string;
  status: string;
  createdAt: string;
}

export interface SellerApplicationStatusResponse {
  applicationId: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
  reviewedAt?: string;
  rejectionReason?: string;
}

// ── Events ──────────────────────────────────────────────────────────────────
export interface EventListRequest {
  page?: number;
  size?: number;
}
export interface EventItem {
  eventId: string;
  title: string;
  category: string;
  techStacks: string[];
  price: number;
  eventDateTime: string;
  remainingQuantity: number;
  status: string;
  thumbnailUrl?: string;
}
export interface EventListResponse {
  content: EventItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EventDetailResponse {
  eventId: string;
  title: string;
  description: string;
  category: string;
  techStacks: TechStackItem[];
  price: number;
  totalQuantity: number;
  remainingQuantity: number;
  eventDateTime: string;
  location: string;
  status: string;
  sellerNickname: string;
  thumbnailUrl?: string;
  createdAt: string;
}

export interface EventSearchRequest {
  keyword: string;
  page?: number;
  size?: number;
}
export type EventSearchResponse = EventListResponse;

export interface EventFilterRequest {
  category?: string;
  techStacks?: number[];
  page?: number;
  size?: number;
}
export type EventFilterResponse = EventListResponse;

// ── Seller Events ────────────────────────────────────────────────────────────
export interface SellerEventCreateRequest {
  title: string;
  description: string;
  category: string;
  techStackIds: number[];
  price: number;
  totalQuantity: number;
  maxQuantity: number;
  eventDateTime: string;
  saleStartAt: string;
  saleEndAt: string;
  location: string;
  imageUrls?: string[];
}
export interface SellerEventCreateResponse {
  eventId: string;
  sellerId: string;
  status: string;
  createdAt: string;
}

export interface SellerEventListRequest {
  status?: string;
  page?: number;
  size?: number;
}
export interface SellerEventItem {
  eventId: string;
  title: string;
  status: string;
  price: number;
  totalQuantity: number;
  remainingQuantity: number;
  eventDateTime: string;
  createdAt: string;
}
export interface SellerEventListResponse {
  content: SellerEventItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SellerEventDetailResponse {
  eventId: string;
  title: string;
  description: string;
  category: string;
  techStacks: TechStackItem[];
  price: number;
  totalQuantity: number;
  remainingQuantity: number;
  maxQuantityPerUser: number;
  eventDateTime: string;
  location: string;
  status: string;
  thumbnailUrl?: string;
  createdAt: string;
}

export interface SellerEventUpdateRequest {
  title?: string;
  description?: string;
  location?: string;
  eventDateTime?: string;
  saleStartAt?: string;
  saleEndAt?: string;
  price?: number;
  totalQuantity?: number;
  maxQuantity?: number;
  category?: string;
  techStackIds?: number[];
  imageUrls?: string[];
  status?: string;
}

export interface SellerEventUpdateResponse {
  eventId: string;
  status: string;
  updatedAt: string;
}

export interface SellerEventStopResponse {
  eventId: string;
  status: string;
}

export interface SellerEventSummaryResponse {
  eventId: string;
  title: string;
  totalQuantity: number;
  soldQuantity: number;
  remainingQuantity: number;
  totalRevenue: number;
}

export interface SellerEventParticipantListRequest {
  page?: number;
  size?: number;
  keyword?: string;
}
export interface ParticipantItem {
  userId: string;
  nickname: string;
  email: string;
  quantity: number;
  orderedAt: string;
}
export interface SellerEventParticipantListResponse {
  content: ParticipantItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SellerEventRefundListRequest {
  page?: number;
  size?: number;
}
export interface SellerEventRefundListResponse {
  content: RefundItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Cart ─────────────────────────────────────────────────────────────────────

// 장바구니 아이템 상세 (백엔드 CartItemDetail)
export interface CartItemDetail {
  cartItemId: string; // UUID string
  eventId: string;
  eventTitle: string;
  price: number;
  quantity: number;
}

// POST /cart/items 요청
export interface CartItemRequest {
  eventId: string; // UUID
  quantity: number;
}

// POST /cart/items 응답 (백엔드 CartItemResponse)
export interface AddCartItemResponse {
  cartId: string;
  items: CartItemDetail[];
  totalAmount: number;
}

// GET /cart 응답 (백엔드 CartResponse)
export interface CartResponse {
  cartId: string | null;
  items: CartItemDetail[];
  totalAmount: number;
}

// PATCH /cart/items/{cartItemId} 요청
export interface CartItemQuantityRequest {
  quantity: number; // 증감 delta (+1, -1)
}

// PATCH /cart/items/{cartItemId} 응답 (백엔드 CartItemQuantityResponse)
export interface CartItemQuantityResponse {
  cartItemId: number;
  quantity: number;
}

// DELETE /cart/items/{cartItemId} 응답
export interface CartItemDeleteResponse {
  success: boolean;
}

// DELETE /cart 응답
export interface CartClearResponse {
  success: boolean;
}
// ── Orders ──────────────────────────────────────────────────────────────────
export interface OrderRequest {
  cartItemIds: string[];
}
export interface OrderResponse {
  orderId: string;
  status: string;
  totalAmount: number;
  createdAt: string;
}

export interface OrderListRequest {
  page?: number;
  size?: number;
}
export interface OrderItem {
  orderId: string;
  status: string;
  totalAmount: number;
  createdAt: string;
}
export interface OrderListResponse {
  content: OrderItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OrderDetailItem {
  eventId: string;
  eventTitle: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}
export interface OrderDetailResponse {
  orderId: string;
  status: string;
  items: OrderDetailItem[];
  totalAmount: number;
  paymentMethod?: string;
  createdAt: string;
}

export interface OrderCancelResponse {
  orderId: string;
  status: string;
}

// ── Tickets ──────────────────────────────────────────────────────────────────
export interface TicketListRequest {
  page?: number;
  size?: number;
}
export interface TicketItem {
  ticketId: number;
  eventId: string;
  eventTitle: string;
  eventDate: string;
  status: string;
}
export interface TicketListResponse {
  tickets: TicketItem[];
  totalElements: number;
  totalPages: number;
}

export interface TicketDetailResponse {
  ticketId: string;
  eventId: string;
  eventTitle: string;
  eventDateTime: string;
  location: string;
  status: string;
  qrCode?: string;
  issuedAt: string;
}

// ── Payments ─────────────────────────────────────────────────────────────────
export interface PaymentRequest {
  orderId: string;
  paymentMethod: "PG" | "WALLET";
}
export interface PaymentResponse {
  paymentId: string;
  orderId: string;
  paymentMethod: string;
  amount: number;
  status: string;
  tossPaymentUrl?: string;
}

export interface PaymentConfirmRequest {
  paymentId: string;
  paymentKey: string;
  orderId: string;
  amount: number;
}
export interface PaymentConfirmResponse {
  paymentId: string;
  orderId: string;
  status: string;
  amount: number;
  approvedAt: string;
}

// ── Wallet ───────────────────────────────────────────────────────────────────
export interface WalletChargeStartRequest {
  amount: number;
}
export interface WalletChargeStartResponse {
  chargeId: string;
  userId:string;
  amount: number;
  status: string;
  createdAt: string;
}

export interface WalletChargeConfirmRequest {
  chargeId: string;
  paymentKey: string;
  amount: number;
}
export interface WalletChargeConfirmResponse {
  userId: string;
  amount: number;
  balance: number;
  status: string;
  completedAt: string;
}

export interface WalletBalanceResponse {
  walletId: string;
  balance: number;
}

export interface WalletTransactionListRequest {
  page?: number;
  size?: number;
}
export interface WalletTransactionItem {
  transactionId: string;
  transactionKey: string;
  type: "CHARGE" | "USE" | "REFUND" | "WITHDRAW";
  amount: number;
  balanceAfter: number;
  relatedOrderId?: string;
  relatedRefundId?: string;
  createdAt: string;
}
export interface WalletTransactionListResponse {
  content: WalletTransactionItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface WalletWithdrawRequest {
  amount: number;
}
export interface WalletWithdrawResponse {
  walletId: string;
  transactionId: string;
  transactionKey: string;
  type: string;
  amount: number;
  balanceAfter: number;
  requestedAt: string;
}

// ── Refunds ──────────────────────────────────────────────────────────────────
export interface WalletRefundRequest {
  orderId: string;
}
export interface WalletRefundResponse {
  refundId: string;
  orderId: string;
  paymentId: string;
  paymentMethod: string;
  refundStatus: string;
  refundAmount: number;
  refundRate: number;
  walletTransactionType: string;
  walletBalanceAfter: number;
  requestedAt: string;
  completedAt: string;
}

export interface PgRefundRequest {
  orderId: string;
}
export interface PgRefundResponse {
  refundId: string;
  orderId: string;
  paymentId: string;
  refundStatus: string;
  refundAmount: number;
  requestedAt: string;
}

export interface RefundItem {
  refundId: string;
  orderId: string;
  refundStatus: string;
  refundAmount: number;
  requestedAt: string;
}
export interface RefundListResponse {
  content: RefundItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RefundDetailResponse {
  refundId: string;
  orderId: string;
  paymentId: string;
  paymentMethod: string;
  refundStatus: string;
  refundAmount: number;
  refundRate: number;
  requestedAt: string;
  completedAt?: string;
}

// ── Seller Settlement ─────────────────────────────────────────────────────────
export interface SettlementItem {
  settlementId: string;
  eventId: string;
  eventTitle: string;
  settledAmount: number;
  feeAmount: number;
  netAmount: number;
  status: string;
  settledAt: string;
}
export interface SettlementResponse {
  content: SettlementItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SellerSettlementDetailResponse {
  settlementId: string;
  eventId: string;
  eventTitle: string;
  settledAmount: number;
  feeAmount: number;
  netAmount: number;
  status: string;
  items: Array<{
    orderItemId: string;
    quantity: number;
    unitPrice: number;
    totalPrice: number;
  }>;
  settledAt: string;
}

// ── Admin ─────────────────────────────────────────────────────────────────────
export interface AdminDashboardResponse {
  totalUsers: number;
  totalSellers: number;
  activeEvents: number;
  pendingApplications: number;
}

export interface AdminEventSearchRequest {
  keyword?: string;
  status?: string;
  sellerId?: string;
  page?: number;
  size?: number;
}
export interface AdminEventItem {
  eventId: string;
  title: string;
  sellerNickname: string;
  status: string;
  eventDateTime: string;
  totalQuantity: number;
  remainingQuantity: number;
  createdAt: string;
}
export interface AdminEventListResponse {
  content: AdminEventItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EventCancelResponse {
  eventId: string;
  status: string;
}

export interface UserSearchCondition {
  keyword?: string;
  role?: string;
  status?: string;
  page?: number;
  size?: number;
}
export interface UserListItem {
  userId: string;
  email: string;
  nickname: string;
  role: string;
  status: string;
  createdAt: string;
}
export interface UserListResponse {
  content: UserListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUserDetailResponse {
  userId: string;
  email: string;
  nickname: string;
  role: string;
  status: string;
  providerType: string;
  position?: string;
  createdAt: string;
  withdrawnAt?: string;
}

export interface UserStatusRequest {
  status: "ACTIVE" | "SUSPENDED";
  reason?: string;
}
export interface UserStatusResponse {
  userId: string;
  status: string;
}

export interface UserRoleRequest {
  role: "USER" | "SELLER" | "ADMIN";
}
export interface UserRoleResponse {
  userId: string;
  role: string;
}

export interface SellerApplicationListItem {
  applicationId: string;
  userId: string;
  nickname: string;
  status: string;
  createdAt: string;
}
export interface SellerApplicationListResponse {
  content: SellerApplicationListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Tech Stack ────────────────────────────────────────────────────────────────
export interface TechStackItem {
  techStackId: number; // Long PK — 공개 마스터 데이터라 UUID 안 씀
  name: string;
}
export interface TechStackListResponse {
  techStacks: TechStackItem[];
}
