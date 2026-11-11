import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from './api'
import { fmtDate } from './format'
import type { Page, PerformanceDetail, ScheduleResponse } from './types'

interface Props {
  loggedIn: boolean
  onReserved: () => void
  notify: (message: string, error?: boolean) => void
}

const PAGE_SIZE = 6

export default function PerformanceList({ loggedIn, onReserved, notify }: Props) {
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [pageNo, setPageNo] = useState(0)
  const [page, setPage] = useState<Page<PerformanceDetail> | null>(null)
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api<Page<PerformanceDetail>>(
      `/api/performances/with-schedules?keyword=${encodeURIComponent(query)}&page=${pageNo}&size=${PAGE_SIZE}`,
    )
      .then((p) => { if (!cancelled) setPage(p) })
      .catch((e) => { if (!cancelled) notify(errorMessage(e), true) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [query, pageNo, reloadKey, notify])

  const search = (e: FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setPageNo(0)
    setQuery(keyword.trim())
  }

  const move = (next: number) => {
    setLoading(true)
    setPageNo(next)
  }

  const reserve = useCallback(async (s: ScheduleResponse) => {
    try {
      await api<number>('/api/reservations', { method: 'POST', body: JSON.stringify({ scheduleId: s.id, quantity: 1 }) })
      notify('예매가 완료되었습니다.')
      setReloadKey((k) => k + 1)
      onReserved()
    } catch (e) {
      notify(errorMessage(e), true) // 품절·중복 예매(409), 미로그인(401), 오픈 전(400)
    }
  }, [notify, onReserved])

  return (
    <section>
      <form className="search" onSubmit={search}>
        <input placeholder="공연 제목 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <button className="btn primary">검색</button>
      </form>

      {loading && <p className="muted">불러오는 중…</p>}
      {page && page.content.length === 0 && <p className="muted">검색 결과가 없습니다.</p>}

      <div className="grid">
        {page?.content.map((p) => (
          <article className="card" key={p.id}>
            <h3>{p.title}</h3>
            <p className="muted">{p.venue}</p>
            {p.description && <p className="desc">{p.description}</p>}
            <ul className="schedules">
              {p.schedules.map((s) => {
                const open = new Date(s.ticketOpenAt) <= new Date()
                const soldOut = s.remainingStock === 0
                return (
                  <li key={s.id}>
                    <div>
                      <strong>{fmtDate(s.startAt)}</strong>
                      <div className="muted small">
                        {open ? `잔여 ${s.remainingStock} / ${s.totalStock}석` : `티켓 오픈 ${fmtDate(s.ticketOpenAt)}`}
                      </div>
                    </div>
                    <button
                      className="btn primary"
                      disabled={!open || soldOut}
                      title={loggedIn ? '' : '예매하려면 로그인이 필요합니다'}
                      onClick={() => reserve(s)}
                    >
                      {soldOut ? '매진' : open ? '예매하기' : '오픈 전'}
                    </button>
                  </li>
                )
              })}
            </ul>
          </article>
        ))}
      </div>

      {page && page.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={pageNo === 0} onClick={() => move(pageNo - 1)}>이전</button>
          <span>{pageNo + 1} / {page.totalPages}</span>
          <button className="btn" disabled={pageNo + 1 >= page.totalPages} onClick={() => move(pageNo + 1)}>다음</button>
        </div>
      )}
    </section>
  )
}
