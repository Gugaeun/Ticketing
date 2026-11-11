import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage, setToken } from './api'

interface Props {
  onLoggedIn: () => void
}

export default function AuthForm({ onLoggedIn }: Props) {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'signup') {
        await api<number>('/api/auth/signup', { method: 'POST', body: JSON.stringify({ email, password, name }) })
      }
      const { accessToken } = await api<{ accessToken: string }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      setToken(accessToken)
      onLoggedIn()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card auth" onSubmit={submit}>
      <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
      <label>
        이메일
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        비밀번호{mode === 'signup' && ' (8~64자)'}
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={mode === 'signup' ? 8 : undefined}
        />
      </label>
      {mode === 'signup' && (
        <label>
          이름
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
      )}
      {error && <p className="error">{error}</p>}
      <button className="btn primary" disabled={busy}>
        {mode === 'login' ? '로그인' : '가입하고 로그인'}
      </button>
      <button
        type="button"
        className="link"
        onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); setError('') }}
      >
        {mode === 'login' ? '계정이 없나요? 회원가입' : '이미 계정이 있나요? 로그인'}
      </button>
    </form>
  )
}
