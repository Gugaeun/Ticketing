// 백엔드 주소. 백엔드의 CORS 설정(localhost:5173 허용)을 사용해 직접 호출한다.
const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'
const TOKEN_KEY = 'token'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export const getToken = () => localStorage.getItem(TOKEN_KEY)

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

// 에러 본문은 JSON 이 아니라 문자열이므로 text() 로 읽는다.
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(BASE + path, { ...init, headers })
  const text = await res.text()
  if (!res.ok) throw new ApiError(res.status, text || res.statusText)
  return (text ? JSON.parse(text) : undefined) as T
}

// 상태 코드별 안내 문구 (서버 메시지가 있으면 그대로 보여 준다)
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return e.message || '로그인이 필요합니다.'
    if (e.status === 403) return '권한이 없습니다.'
    return e.message
  }
  return '서버에 연결할 수 없습니다.'
}
