import { useCallback, useEffect, useState } from 'react'
import { api, errorMessage } from './api'
import { fmtDate } from './format'
import type { Reservation } from './types'

interface Props {
  refreshKey: number
  onChanged: () => void
  notify: (message: string, error?: boolean) => void
}

export default function MyReservations({ refreshKey, onChanged, notify }: Props) {
  const [rows, setRows] = useState<Reservation[] | null>(null)

  useEffect(() => {
    let cancelled = false
    api<Reservation[]>('/api/reservations')
      .then((r) => { if (!cancelled) setRows(r) })
      .catch((e) => { if (!cancelled) notify(errorMessage(e), true) })
    return () => { cancelled = true }
  }, [refreshKey, notify])

  const cancel = useCallback(async (id: number) => {
    try {
      await api<void>(`/api/reservations/${id}`, { method: 'DELETE' })
      notify('예매가 취소되었습니다.')
      onChanged()
    } catch (e) {
      notify(errorMessage(e), true)
    }
  }, [notify, onChanged])

  if (!rows) return <p className="muted">불러오는 중…</p>
  if (rows.length === 0) return <p className="muted">예매 내역이 없습니다.</p>

  return (
    <table className="table">
      <thead>
        <tr><th>예매번호</th><th>공연</th><th>수량</th><th>상태</th><th>예매일시</th><th /></tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id}>
            <td>{r.id}</td>
            <td>
              {r.performanceTitle ?? `회차 #${r.scheduleId}`}
              <div className="muted small">{r.venue} · {fmtDate(r.startAt)}</div>
            </td>
            <td>{r.quantity}매</td>
            <td className={r.status === 'CONFIRMED' ? 'ok' : 'muted'}>
              {r.status === 'CONFIRMED' ? '예매 완료' : '취소'}
            </td>
            <td>{fmtDate(r.createdAt)}</td>
            <td>
              {r.status === 'CONFIRMED' && (
                <button className="btn danger" onClick={() => cancel(r.id)}>예매 취소</button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
