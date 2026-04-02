// ── 날짜 포맷 ────────────────────────────────────────────────────
export function formatDate(iso: string, options?: Intl.DateTimeFormatOptions): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('ko-KR', options ?? {
    year: 'numeric', month: 'long', day: 'numeric',
  })
}

export function formatDateTime(iso: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function formatFullDateTime(iso: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
    weekday: 'short', hour: '2-digit', minute: '2-digit',
  })
}

export function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1)   return '방금 전'
  if (minutes < 60)  return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24)    return `${hours}시간 전`
  const days = Math.floor(hours / 24)
  if (days < 7)      return `${days}일 전`
  return formatDate(iso)
}

// ── 금액 포맷 ────────────────────────────────────────────────────
export function formatPrice(amount: number): string {
  if (amount === 0) return '무료'
  if (amount >= 10_000) return `${(amount / 10_000).toFixed(amount % 10_000 === 0 ? 0 : 1)}만원`
  return `${amount.toLocaleString('ko-KR')}원`
}

export function formatPriceExact(amount: number): string {
  if (amount === 0) return '무료'
  return `${amount.toLocaleString('ko-KR')}원`
}

// ── 문자열 ────────────────────────────────────────────────────────
export function truncate(str: string, maxLen: number): string {
  if (str.length <= maxLen) return str
  return str.slice(0, maxLen - 1) + '…'
}

export function getInitials(name: string): string {
  return name ? name.charAt(0).toUpperCase() : '?'
}

// ── UUID 단축 ─────────────────────────────────────────────────────
export function shortId(uuid: string, len = 8): string {
  return uuid?.slice(0, len) ?? '—'
}

// ── 유효성 검사 ───────────────────────────────────────────────────
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

export function isValidPassword(pw: string): boolean {
  // 8자 이상, 영문+숫자 조합
  return pw.length >= 8 && /[a-zA-Z]/.test(pw) && /\d/.test(pw)
}

export function isValidUrl(url: string): boolean {
  try { new URL(url); return true } catch { return false }
}

// ── 클래스명 병합 (tiny cx) ───────────────────────────────────────
export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ')
}

// ── 복사 ──────────────────────────────────────────────────────────
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

// ── 쿼리스트링 ────────────────────────────────────────────────────
export function toQueryString(params: Record<string, string | number | boolean | undefined | null>): string {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

// ── 에러 메시지 추출 ──────────────────────────────────────────────
export function getErrorMessage(err: unknown): string {
  if (err instanceof Error) return err.message
  if (typeof err === 'object' && err !== null) {
    const e = err as any
    return e?.response?.data?.message ?? e?.message ?? '알 수 없는 오류가 발생했습니다'
  }
  return '알 수 없는 오류가 발생했습니다'
}
