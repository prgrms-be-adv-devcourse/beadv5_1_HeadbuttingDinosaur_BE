import { useEffect, useState, useCallback } from 'react'
import { getAdminUsers, updateUserStatus, updateUserRole } from '../../api/admin.api'
import type { UserListItem } from '../../api/types'
import { useToast } from '../../contexts/ToastContext'

const ROLE_MAP: Record<string, { label: string; cls: string }> = {
  USER:   { label: '일반',   cls: 'badge-gray' },
  SELLER: { label: '판매자', cls: 'badge-brand' },
  ADMIN:  { label: '관리자', cls: 'badge-blue' },
}
const STATUS_MAP: Record<string, { label: string; cls: string }> = {
  ACTIVE:    { label: '정상',   cls: 'badge-green' },
  SUSPENDED: { label: '정지',   cls: 'badge-red' },
  WITHDRAWN: { label: '탈퇴',   cls: 'badge-gray' },
}

export default function AdminUsers() {
  const { toast } = useToast()
  const [users, setUsers] = useState<UserListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')
  const [draftKeyword, setDraftKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [actionLoading, setActionLoading] = useState<string | null>(null)

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getAdminUsers({ keyword: keyword || undefined, role: roleFilter || undefined, page: 0, size: 50 })
      setUsers(res.data.data.content)
    } catch { toast('로드 실패', 'error') }
    finally { setLoading(false) }
  }, [keyword, roleFilter])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  const handleStatusToggle = async (user: UserListItem) => {
    const newStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
    const label = newStatus === 'SUSPENDED' ? '정지' : '복구'
    if (!confirm(`${user.nickname}을(를) ${label}할까요?`)) return
    setActionLoading(user.userId)
    try {
      await updateUserStatus(user.userId, { status: newStatus })
      toast(`${label} 처리되었습니다`, 'success')
      fetchUsers()
    } catch { toast('처리 실패', 'error') }
    finally { setActionLoading(null) }
  }

  const handleRoleChange = async (user: UserListItem, newRole: 'USER' | 'SELLER' | 'ADMIN') => {
    if (!confirm(`${user.nickname}의 권한을 ${newRole}로 변경할까요?`)) return
    setActionLoading(user.userId)
    try {
      await updateUserRole(user.userId, { role: newRole })
      toast('권한이 변경되었습니다', 'success')
      fetchUsers()
    } catch { toast('처리 실패', 'error') }
    finally { setActionLoading(null) }
  }

  return (
    <div style={{ padding: '32px 36px' }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>회원 관리</h1>
        <p style={{ fontSize: 14, color: 'var(--text-3)' }}>총 {users.length}명</p>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
        <form onSubmit={e => { e.preventDefault(); setKeyword(draftKeyword) }} style={{ display: 'flex', gap: 8, flex: 1 }}>
          <div className="search-bar" style={{ flex: 1, maxWidth: 320 }}>
            <svg className="search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            <input className="search-input" placeholder="닉네임/이메일 검색"
              value={draftKeyword} onChange={e => setDraftKeyword(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-secondary">검색</button>
        </form>

        <select className="form-input form-select" value={roleFilter}
          onChange={e => setRoleFilter(e.target.value)}
          style={{ width: 120 }}>
          <option value="">전체 권한</option>
          <option value="USER">일반</option>
          <option value="SELLER">판매자</option>
          <option value="ADMIN">관리자</option>
        </select>
      </div>

      {/* Table */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
      ) : (
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
              <tr>
                <th>닉네임</th>
                <th>이메일</th>
                <th>권한</th>
                <th>상태</th>
                <th>가입일</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {users.map(user => {
                const role = ROLE_MAP[user.role] ?? { label: user.role, cls: 'badge-gray' }
                const status = STATUS_MAP[user.status] ?? { label: user.status, cls: 'badge-gray' }
                const isLoading = actionLoading === user.userId
                return (
                  <tr key={user.userId}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{
                          width: 28, height: 28, borderRadius: '50%',
                          background: 'var(--brand-light)', color: 'var(--brand)',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontSize: 11, fontWeight: 600, flexShrink: 0,
                        }}>{user.nickname.charAt(0)}</div>
                        <span style={{ fontWeight: 500 }}>{user.nickname}</span>
                      </div>
                    </td>
                    <td style={{ color: 'var(--text-3)', fontSize: 13 }}>{user.email}</td>
                    <td>
                      <select value={user.role}
                        onChange={e => handleRoleChange(user, e.target.value as any)}
                        disabled={isLoading}
                        style={{
                          fontSize: 12, fontWeight: 500, padding: '3px 6px',
                          borderRadius: 'var(--r-sm)', border: '1px solid var(--border)',
                          background: 'var(--surface)', cursor: 'pointer',
                        }}>
                        <option value="USER">일반</option>
                        <option value="SELLER">판매자</option>
                        <option value="ADMIN">관리자</option>
                      </select>
                    </td>
                    <td><span className={`badge ${status.cls}`}>{status.label}</span></td>
                    <td style={{ fontSize: 13, color: 'var(--text-3)' }}>
                      {new Date(user.createdAt).toLocaleDateString('ko-KR')}
                    </td>
                    <td>
                      {user.status !== 'WITHDRAWN' && (
                        <button
                          onClick={() => handleStatusToggle(user)}
                          disabled={isLoading}
                          className={`btn btn-sm ${user.status === 'ACTIVE' ? 'btn-danger' : 'btn-secondary'}`}
                        >
                          {isLoading ? '...' : user.status === 'ACTIVE' ? '정지' : '복구'}
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
