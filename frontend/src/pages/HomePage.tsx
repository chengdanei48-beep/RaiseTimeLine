import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'

/** S-03 タイムライン(今回はログインできたことを示すだけの仮画面) */
export function HomePage() {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    try {
      // 成功・失敗にかかわらずログアウト状態になり、RequireAuth が S-02 へ移動させる
      await logout()
    } catch {
      // サーバーへの通知に失敗してもブラウザ側のトークンは破棄済み
    }
  }

  return (
    <div className="auth-wrap card card-pad">
      <h1>ログイン成功</h1>
      <p className="lead">
        {user?.displayName} さん(@{user?.username})としてログインしています
      </p>
      <button className="btn btn-outline full" type="button" onClick={handleLogout} disabled={loggingOut}>
        ログアウト
      </button>
    </div>
  )
}
