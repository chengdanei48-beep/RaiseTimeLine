import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from './Avatar'

/** 共通ヘッダー(ログイン後の全画面)。アプリ名(→ホーム)・検索(→S-07)・自分のプロフィールへのリンク・ログアウト。 */
export function AppHeader() {
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

  if (user === null) {
    return null
  }

  return (
    <header className="app-header">
      <div className="app-header-inner">
        <Link className="logo" to="/">
          RaiseTimeLine
        </Link>
        <Link className="header-search" to="/search">
          <span aria-hidden="true">🔍</span> ユーザーを検索
        </Link>
        <Link className="header-me" to={`/users/${user.id}`} aria-label="自分のプロフィール">
          <Avatar user={user} size="sm" />
          <span className="name">{user.displayName}</span>
        </Link>
        <button
          className="btn btn-outline btn-compact"
          type="button"
          onClick={() => void handleLogout()}
          disabled={loggingOut}
        >
          ログアウト
        </button>
      </div>
    </header>
  )
}
