import { useCallback, useRef, useState } from 'react'
import { getToken, setToken } from './api'
import AuthForm from './AuthForm'
import MyReservations from './MyReservations'
import PerformanceList from './PerformanceList'
import './App.css'

type Tab = 'performances' | 'mine'

export default function App() {
  const [loggedIn, setLoggedIn] = useState(() => !!getToken())
  const [tab, setTab] = useState<Tab>('performances')
  const [showAuth, setShowAuth] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [toast, setToast] = useState<{ message: string; error: boolean } | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const notify = useCallback((message: string, error = false) => {
    setToast({ message, error })
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), 3000)
  }, [])
  const bump = useCallback(() => setRefreshKey((k) => k + 1), [])

  const logout = () => {
    setToken(null)
    setLoggedIn(false)
    setTab('performances')
  }

  return (
    <div className="app">
      <header>
        <h1>🎟️ Ticketing</h1>
        <nav>
          <button className={tab === 'performances' ? 'active' : ''} onClick={() => setTab('performances')}>
            공연
          </button>
          <button
            className={tab === 'mine' ? 'active' : ''}
            onClick={() => (loggedIn ? setTab('mine') : setShowAuth(true))}
          >
            내 예매
          </button>
        </nav>
        {loggedIn ? (
          <button className="btn" onClick={logout}>로그아웃</button>
        ) : (
          <button className="btn primary" onClick={() => setShowAuth(true)}>로그인</button>
        )}
      </header>

      <main>
        {tab === 'performances' && <PerformanceList loggedIn={loggedIn} onReserved={bump} notify={notify} />}
        {tab === 'mine' && loggedIn && <MyReservations refreshKey={refreshKey} onChanged={bump} notify={notify} />}
      </main>

      {showAuth && (
        <div className="overlay" onClick={() => setShowAuth(false)}>
          <div onClick={(e) => e.stopPropagation()}>
            <AuthForm
              onLoggedIn={() => {
                setLoggedIn(true)
                setShowAuth(false)
                notify('로그인되었습니다.')
              }}
            />
          </div>
        </div>
      )}
      {toast && <div className={`toast ${toast.error ? 'err' : ''}`}>{toast.message}</div>}
    </div>
  )
}
