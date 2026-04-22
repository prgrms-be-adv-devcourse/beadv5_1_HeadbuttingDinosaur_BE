import { useEffect, useState } from 'react'
import { getSellerApplications, processSellerApplication } from '../../api/admin.api'
import type { SellerApplicationListResponse } from '../../api/types'

export default function AdminApplications() {
  const [applications, setApplications] = useState<SellerApplicationListResponse[]>([])
  const [loading, setLoading] = useState(true)

  const fetchApplications = () => {
    getSellerApplications()
    .then(r => setApplications(r.data))
    .catch(() => alert('로드 실패'))
    .finally(() => setLoading(false))
  }

  useEffect(() => { fetchApplications() }, [])

  const handleDecide = async (applicationId: string, decision: string) => {
    if (!confirm(`${decision === 'APPROVED' ? '승인' : '반려'}하시겠습니까?`)) return
    try {
      await processSellerApplication(applicationId, decision)
      alert('처리 완료')
      fetchApplications()
    } catch { alert('처리 실패') }
  }

  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><div className="spinner" /></div>
  if (applications.length === 0) return <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-3)' }}>신청 내역이 없습니다</div>

  return (
      <div className="container" style={{ paddingTop: 40 }}>
        <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 24 }}>판매자 신청 심사</h1>
        <div className="card" style={{ overflow: 'hidden' }}>
          <table>
            <thead>
            <tr>
              <th>신청 ID</th>
              <th>유저 ID</th>
              <th>은행명</th>
              <th>계좌번호</th>
              <th>예금주</th>
              <th>상태</th>
              <th>신청일</th>
              <th>처리</th>
            </tr>
            </thead>
            <tbody>
            {applications.map(app => (
                <tr key={app.applicationId}>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{app.applicationId.slice(0, 8)}...</td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{app.userId.slice(0, 8)}...</td>
                  <td>{app.bankName}</td>
                  <td>{app.accountNumber}</td>
                  <td>{app.accountHolder}</td>
                  <td>
                  <span className={`badge ${app.status === 'PENDING' ? 'badge-amber' : app.status === 'APPROVED' ? 'badge-green' : 'badge-red'}`}>
                    {app.status === 'PENDING' ? '대기' : app.status === 'APPROVED' ? '승인' : '반려'}
                  </span>
                  </td>
                  <td style={{ fontSize: 13, color: 'var(--text-3)' }}>{new Date(app.createdAt).toLocaleDateString('ko-KR')}</td>
                  <td>
                    {app.status === 'PENDING' && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-primary btn-sm" onClick={() => handleDecide(app.applicationId, 'APPROVED')}>승인</button>
                          <button className="btn btn-danger btn-sm" onClick={() => handleDecide(app.applicationId, 'REJECTED')}>반려</button>
                        </div>
                    )}
                  </td>
                </tr>
            ))}
            </tbody>
          </table>
        </div>
      </div>
  )
}