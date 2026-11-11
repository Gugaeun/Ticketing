export interface ScheduleResponse {
  id: number
  startAt: string
  ticketOpenAt: string
  totalStock: number
  remainingStock: number
}

export interface PerformanceDetail {
  id: number
  title: string
  venue: string
  description: string | null
  schedules: ScheduleResponse[]
}

// Spring Data 의 Page 응답 중 화면에서 쓰는 필드만
export interface Page<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}

// GET /api/reservations (ReservationController.MyReservation)
export interface Reservation {
  id: number
  memberId: number
  scheduleId: number
  quantity: number
  status: 'CONFIRMED' | 'CANCELED'
  createdAt: string
  performanceTitle: string | null
  venue: string | null
  startAt: string | null
}
