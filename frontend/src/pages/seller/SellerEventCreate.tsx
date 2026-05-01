import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createSellerEvent, getSellerEventDetail, updateSellerEvent } from '../../api/events.api'
import { useToast } from '../../contexts/ToastContext'

const CATEGORIES = [
  { value: 'CONFERENCE', label: '컨퍼런스' },
  { value: 'MEETUP', label: '밋업' },
]
const TECH_STACKS = ['Java', 'Spring Boot', 'Kotlin', 'JavaScript', 'TypeScript', 'React', 'Vue.js', 'Node.js', 'Python', 'FastAPI', 'Go', 'Rust', 'Docker', 'Kubernetes', 'AWS', 'MySQL', 'PostgreSQL', 'Redis', 'Kafka']
const TECH_STACK_MAP: Record<string, number> = {
  'Java': 1, 'Spring Boot': 2, 'Kotlin': 3, 'JavaScript': 4, 'TypeScript': 5,
  'React': 6, 'Vue.js': 7, 'Node.js': 8, 'Python': 9, 'FastAPI': 10,
  'Go': 11, 'Rust': 12, 'Docker': 13, 'Kubernetes': 14, 'AWS': 15,
  'MySQL': 16, 'PostgreSQL': 17, 'Redis': 18, 'Kafka': 19,
}

interface EventForm {
  title: string
  description: string
  category: string
  techStacks: string[]
  price: string
  totalQuantity: string
  maxQuantityPerUser: string
  eventDateTime: string
  saleStartAt: string
  saleEndAt: string
  location: string
  imageUrls: string
}

const EMPTY_FORM: EventForm = {
  title: '', description: '', category: '',
  techStacks: [], price: '', totalQuantity: '',
  maxQuantityPerUser: '1', eventDateTime: '',
  saleStartAt: '', saleEndAt: '', location: '', imageUrls: '',
}

function EventForm({ initialData, onSubmit, submitLabel }: {
  initialData: EventForm
  onSubmit: (data: EventForm) => Promise<void>
  submitLabel: string
}) {
  const [form, setForm] = useState<EventForm>(initialData)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)

  const toggleStack = (s: string) =>
      setForm(f => ({
        ...f,
        techStacks: f.techStacks.includes(s) ? f.techStacks.filter(t => t !== s) : [...f.techStacks, s],
      }))

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.title) e.title = '이벤트명을 입력하세요'
    if (!form.category) e.category = '카테고리를 선택하세요'
    if (!form.description) e.description = '상세 설명을 입력하세요'
    if (!form.eventDateTime) e.eventDateTime = '행사 일시를 선택하세요'
    if (!form.saleStartAt) e.saleStartAt = '판매 시작일을 선택하세요'
    if (!form.saleEndAt) e.saleEndAt = '판매 종료일을 선택하세요'
    if (!form.location) e.location = '장소를 입력하세요'
    if (!form.totalQuantity || parseInt(form.totalQuantity) < 5) e.totalQuantity = '수량을 5명 이상 입력하세요'
    if (form.techStacks.length === 0) e.techStacks = '기술 스택을 1개 이상 선택하세요'
    if (form.saleStartAt && form.saleEndAt) {
      if (new Date(form.saleStartAt) >= new Date(form.saleEndAt))
        e.saleEndAt = '판매 종료일은 판매 시작일 이후여야 합니다'
    }
    if (form.saleEndAt && form.eventDateTime) {
      if (new Date(form.saleEndAt) >= new Date(form.eventDateTime))
        e.saleEndAt = '판매 종료일은 행사 일시 이전이어야 합니다'
    }
    if (form.saleStartAt) {
      const minStart = new Date()
      minStart.setDate(minStart.getDate() + 3)
      if (new Date(form.saleStartAt) < minStart)
        e.saleStartAt = '판매 시작일은 오늘로부터 3일 이후여야 합니다'
    }

    setErrors(e)
    return Object.keys(e).length === 0
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      await onSubmit(form)
    } finally {
      setLoading(false)
    }
  }

  return (
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {/* 기본 정보 */}
        <Section title="기본 정보">
          <div className="form-group">
            <label className="form-label">이벤트명 *</label>
            <input className="form-input" placeholder="Spring Boot 심화 밋업"
                   value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                   style={errors.title ? { borderColor: 'var(--danger)' } : {}} />
            {errors.title && <span className="form-error">{errors.title}</span>}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">카테고리 *</label>
              <select className="form-input form-select" value={form.category}
                      onChange={e => setForm(f => ({ ...f, category: e.target.value }))}
                      style={errors.category ? { borderColor: 'var(--danger)' } : {}}>
                <option value="">선택</option>
                {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
              {errors.category && <span className="form-error">{errors.category}</span>}
            </div>
            <div className="form-group">
              <label className="form-label">장소 *</label>
              <input className="form-input" placeholder="서울 강남구 / 온라인"
                     value={form.location} onChange={e => setForm(f => ({ ...f, location: e.target.value }))}
                     style={errors.location ? { borderColor: 'var(--danger)' } : {}} />
              {errors.location && <span className="form-error">{errors.location}</span>}
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">행사 일시 *</label>
            <input className="form-input" type="datetime-local"
                   value={form.eventDateTime} onChange={e => setForm(f => ({ ...f, eventDateTime: e.target.value }))}
                   style={errors.eventDateTime ? { borderColor: 'var(--danger)' } : {}} />
            {errors.eventDateTime && <span className="form-error">{errors.eventDateTime}</span>}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">판매 시작일 *</label>
              <input className="form-input" type="datetime-local"
                     value={form.saleStartAt} onChange={e => setForm(f => ({ ...f, saleStartAt: e.target.value }))}
                     style={errors.saleStartAt ? { borderColor: 'var(--danger)' } : {}} />
              {errors.saleStartAt && <span className="form-error">{errors.saleStartAt}</span>}
            </div>
            <div className="form-group">
              <label className="form-label">판매 종료일 *</label>
              <input className="form-input" type="datetime-local"
                     value={form.saleEndAt} onChange={e => setForm(f => ({ ...f, saleEndAt: e.target.value }))}
                     style={errors.saleEndAt ? { borderColor: 'var(--danger)' } : {}} />
              {errors.saleEndAt && <span className="form-error">{errors.saleEndAt}</span>}
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">상세 설명 *</label>
            <textarea className="form-textarea" rows={5} placeholder="이벤트에 대한 자세한 설명을 입력하세요"
                      value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                      style={errors.description ? { borderColor: 'var(--danger)' } : {}} />
            {errors.description && <span className="form-error">{errors.description}</span>}
          </div>

          <div className="form-group">
            <label className="form-label">이미지 URL</label>
            <input className="form-input" placeholder="https://example.com/image.jpg"
                   value={form.imageUrls} onChange={e => setForm(f => ({ ...f, imageUrls: e.target.value }))} />
            <span style={{ fontSize: 12, color: 'var(--text-4)' }}>최대 2장, 쉼표로 구분</span>
          </div>
        </Section>

        {/* 티켓 설정 */}
        <Section title="티켓 설정">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">티켓 가격 (원)</label>
              <input className="form-input" type="number" min="0" placeholder="0 = 무료"
                     value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))} />
              <span style={{ fontSize: 12, color: 'var(--text-4)' }}>0 입력 시 무료</span>
            </div>
            <div className="form-group">
              <label className="form-label">총 수량 *</label>
              <input className="form-input" type="number" min="5" placeholder="100"
                     value={form.totalQuantity} onChange={e => setForm(f => ({ ...f, totalQuantity: e.target.value }))}
                     style={errors.totalQuantity ? { borderColor: 'var(--danger)' } : {}} />
              {errors.totalQuantity && <span className="form-error">{errors.totalQuantity}</span>}
            </div>
            <div className="form-group">
              <label className="form-label">1인 최대 구매</label>
              <input className="form-input" type="number" min="1" max="10"
                     value={form.maxQuantityPerUser} onChange={e => setForm(f => ({ ...f, maxQuantityPerUser: e.target.value }))} />
            </div>
          </div>
        </Section>

        {/* 기술 스택 */}
        <Section title="관련 기술 스택 *">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {TECH_STACKS.map(stack => (
                <button key={stack} type="button" onClick={() => toggleStack(stack)} className="tag" style={{
                  cursor: 'pointer',
                  background: form.techStacks.includes(stack) ? 'var(--brand-light)' : 'var(--surface-2)',
                  color: form.techStacks.includes(stack) ? 'var(--brand)' : 'var(--text-2)',
                  border: `1px solid ${form.techStacks.includes(stack) ? 'var(--brand-muted)' : 'var(--border)'}`,
                }}>{stack}</button>
            ))}
          </div>
          {errors.techStacks && <span className="form-error">{errors.techStacks}</span>}
          {form.techStacks.length > 0 && (
              <div style={{ fontSize: 12, color: 'var(--text-3)' }}>{form.techStacks.length}개 선택됨</div>
          )}
        </Section>

        <button type="submit" className="btn btn-primary btn-lg btn-full" disabled={loading}>
          {loading ? '처리 중...' : submitLabel}
        </button>
      </form>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
      <div className="card" style={{ padding: '24px' }}>
        <h2 style={{ fontSize: 15, fontWeight: 600, marginBottom: 18, color: 'var(--text)' }}>{title}</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>{children}</div>
      </div>
  )
}

export default function SellerEventCreate() {
  const { toast } = useToast()
  const navigate = useNavigate()

  const handleSubmit = async (form: EventForm) => {
    const res = await createSellerEvent({
      title: form.title,
      description: form.description,
      category: form.category,
      techStackIds: form.techStacks.map(s => TECH_STACK_MAP[s]).filter(Boolean),
      price: parseInt(form.price) || 0,
      totalQuantity: parseInt(form.totalQuantity),
      maxQuantity: parseInt(form.maxQuantityPerUser) || 1,
      eventDateTime: form.eventDateTime,
      saleStartAt: form.saleStartAt,
      saleEndAt: form.saleEndAt,
      location: form.location,
      imageUrls: form.imageUrls ? form.imageUrls.split(',').map(s => s.trim()).filter(Boolean) : undefined,
    })
    toast('이벤트가 등록되었습니다!', 'success')
    navigate(`/seller/events/${res.data.data.eventId}`)
  }

  return (
      <div style={{ padding: '32px 36px', maxWidth: 720 }}>
        <div style={{ marginBottom: 28 }}>
          <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>이벤트 등록</h1>
          <p style={{ fontSize: 14, color: 'var(--text-3)' }}>새로운 이벤트를 등록하세요</p>
        </div>
        <EventForm initialData={EMPTY_FORM} onSubmit={handleSubmit} submitLabel="이벤트 등록하기" />
      </div>
  )
}

export function SellerEventEdit() {
  const { id } = useParams<{ id: string }>()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [initial, setInitial] = useState<EventForm | null>(null)

  useEffect(() => {
    if (!id) return
    getSellerEventDetail(id).then(res => {
      const d = res.data.data
      setInitial({
        title: d.title, description: d.description, category: d.category,
        techStacks: d.techStacks.map((t: any) => t.name),
        price: String(d.price),
        totalQuantity: String(d.totalQuantity),
        maxQuantityPerUser: String(d.maxQuantity ?? 1),
        eventDateTime: d.eventDateTime.slice(0, 16),
        saleStartAt: '', saleEndAt: '',
        location: d.location, imageUrls: '',
      })
    }).catch(() => toast('이벤트 로드 실패', 'error'))
  }, [id])

  const handleSubmit = async (form: EventForm) => {
    if (!id) return
    await updateSellerEvent(id, {
      title: form.title,
      description: form.description,
      price: parseInt(form.price) || 0,
      totalQuantity: parseInt(form.totalQuantity),
      maxQuantity: parseInt(form.maxQuantityPerUser) || 1,
      eventDateTime: form.eventDateTime,
      saleStartAt: form.saleStartAt,
      saleEndAt: form.saleEndAt,
      location: form.location,
      imageUrls: form.imageUrls ? form.imageUrls.split(',').map(s => s.trim()).filter(Boolean) : undefined,
    })
    toast('이벤트가 수정되었습니다', 'success')
    navigate(`/seller/events/${id}`)
  }

  if (!initial) return <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 100 }}><div className="spinner" /></div>

  return (
      <div style={{ padding: '32px 36px', maxWidth: 720 }}>
        <div style={{ marginBottom: 28 }}>
          <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>이벤트 수정</h1>
        </div>
        <EventForm initialData={initial} onSubmit={handleSubmit} submitLabel="수정 저장" />
      </div>
  )
}