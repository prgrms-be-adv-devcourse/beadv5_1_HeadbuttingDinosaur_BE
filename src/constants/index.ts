// ── 이벤트 상태 ───────────────────────────────────────────────────
export const EVENT_STATUS = {
  DRAFT:     { label: '초안',   badge: 'badge-gray',  color: 'var(--text-3)' },
  ON_SALE:   { label: '판매중', badge: 'badge-green', color: 'var(--success)' },
  SOLD_OUT:  { label: '매진',   badge: 'badge-red',   color: 'var(--danger)' },
  ENDED:     { label: '종료',   badge: 'badge-gray',  color: 'var(--text-3)' },
  CANCELLED: { label: '취소됨', badge: 'badge-gray',  color: 'var(--text-3)' },
} as const

// ── 주문 상태 ─────────────────────────────────────────────────────
export const ORDER_STATUS = {
  CREATED:         { label: '주문 생성',  badge: 'badge-amber' },
  PAYMENT_PENDING: { label: '결제 대기', badge: 'badge-amber' },
  PAID:            { label: '결제 완료', badge: 'badge-green' },
  CANCELLED:       { label: '취소됨',    badge: 'badge-gray' },
  REFUNDED:        { label: '환불 완료', badge: 'badge-blue' },
} as const

// ── 티켓 상태 ─────────────────────────────────────────────────────
export const TICKET_STATUS = {
  VALID:     { label: '사용 가능', badge: 'badge-green' },
  USED:      { label: '사용 완료', badge: 'badge-gray' },
  CANCELLED: { label: '취소됨',   badge: 'badge-red' },
  EXPIRED:   { label: '만료됨',   badge: 'badge-gray' },
} as const

// ── 환불 상태 ─────────────────────────────────────────────────────
export const REFUND_STATUS = {
  PENDING:   { label: '처리 중', badge: 'badge-amber' },
  COMPLETED: { label: '완료',    badge: 'badge-green' },
  REJECTED:  { label: '거절됨',  badge: 'badge-red' },
  CANCELLED: { label: '취소됨',  badge: 'badge-gray' },
} as const

// ── 회원 권한 ─────────────────────────────────────────────────────
export const USER_ROLE = {
  USER:   { label: '일반 회원', badge: 'badge-gray' },
  SELLER: { label: '판매자',    badge: 'badge-brand' },
  ADMIN:  { label: '관리자',    badge: 'badge-blue' },
} as const

// ── 회원 상태 ─────────────────────────────────────────────────────
export const USER_STATUS = {
  ACTIVE:    { label: '정상',  badge: 'badge-green' },
  SUSPENDED: { label: '정지',  badge: 'badge-red' },
  WITHDRAWN: { label: '탈퇴',  badge: 'badge-gray' },
} as const

// ── 정산 상태 ─────────────────────────────────────────────────────
export const SETTLEMENT_STATUS = {
  PENDING:   { label: '대기', badge: 'badge-amber' },
  COMPLETED: { label: '완료', badge: 'badge-green' },
  CANCELLED: { label: '취소', badge: 'badge-gray' },
} as const

// ── 예치금 거래 유형 ───────────────────────────────────────────────
export const WALLET_TX_TYPE = {
  CHARGE:   { label: '충전', sign: '+', color: 'var(--success)' },
  USE:      { label: '사용', sign: '−', color: 'var(--danger)' },
  REFUND:   { label: '환불', sign: '+', color: 'var(--info)' },
  WITHDRAW: { label: '출금', sign: '−', color: 'var(--text-3)' },
} as const

// ── 카테고리 목록 ─────────────────────────────────────────────────
export const EVENT_CATEGORIES = [
  '컨퍼런스', '밋업', '해커톤', '스터디', '세미나', '워크샵', '기타',
] as const

// ── 기술 스택 ─────────────────────────────────────────────────────
export const TECH_STACKS = [
  'Java', 'Spring Boot', 'Kotlin', 'JavaScript', 'TypeScript',
  'React', 'Vue.js', 'Angular', 'Node.js', 'Express',
  'NestJS', 'Python', 'FastAPI', 'Django', 'Flask',
  'Go', 'Rust', 'C++', 'Swift', 'Kotlin Multiplatform',
  'Docker', 'Kubernetes', 'AWS', 'GCP', 'Azure',
  'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Kafka',
  'ElasticSearch', 'GraphQL', 'gRPC', 'Terraform', 'CI/CD',
] as const

// ── 포지션 ────────────────────────────────────────────────────────
export const POSITIONS = [
  { value: 'BACKEND',  label: '백엔드' },
  { value: 'FRONTEND', label: '프론트엔드' },
  { value: 'FULLSTACK',label: '풀스택' },
  { value: 'DEVOPS',   label: 'DevOps/인프라' },
  { value: 'DATA',     label: '데이터 엔지니어' },
  { value: 'MOBILE',   label: '모바일' },
  { value: 'AI_ML',    label: 'AI/ML' },
  { value: 'SECURITY', label: '보안' },
  { value: 'DESIGN',   label: 'UI/UX 디자인' },
  { value: 'OTHER',    label: '기타' },
] as const

// ── 페이지 기본값 ─────────────────────────────────────────────────
export const DEFAULT_PAGE_SIZE = 12
export const DEFAULT_ADMIN_PAGE_SIZE = 20
