import { Navigate, Outlet } from 'react-router'
import { useAuth } from './AuthContext'

function Loading() {
  return (
    <output className="loading">読み込み中…</output>
  )
}

/** ログイン必須の画面。未ログインなら S-02 ログインへ。 */
export function RequireAuth() {
  const { status } = useAuth()
  if (status === 'loading') return <Loading />
  return status === 'authenticated' ? <Outlet /> : <Navigate to="/login" replace />
}

/** 未ログイン用の画面(新規登録・ログイン)。ログイン済みなら S-03 へ。 */
export function GuestOnly() {
  const { status } = useAuth()
  if (status === 'loading') return <Loading />
  return status === 'authenticated' ? <Navigate to="/" replace /> : <Outlet />
}
