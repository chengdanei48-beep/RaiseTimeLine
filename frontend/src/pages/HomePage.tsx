import { useEffect, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { fetchTimeline } from '../api/posts'
import type { Post } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PostCard } from '../components/PostCard'
import { PostComposer } from '../components/PostComposer'

/** S-03 タイムライン(全体。画像・フォロー中タブ・いいね/コメント数は後続) */
export function HomePage() {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)
  const [posts, setPosts] = useState<Post[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchTimeline(null)
      .then((page) => {
        if (!cancelled) {
          setPosts(page.items)
          setNextCursor(page.nextCursor)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleLogout() {
    setLoggingOut(true)
    try {
      // 成功・失敗にかかわらずログアウト状態になり、RequireAuth が S-02 へ移動させる
      await logout()
    } catch {
      // サーバーへの通知に失敗してもブラウザ側のトークンは破棄済み
    }
  }

  async function handleLoadMore() {
    setLoading(true)
    setError(null)
    try {
      const page = await fetchTimeline(nextCursor)
      setPosts((current) => [...current, ...page.items])
      setNextCursor(page.nextCursor)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setLoading(false)
    }
  }

  function handleUpdated(updated: Post) {
    setPosts((current) => current.map((p) => (p.id === updated.id ? updated : p)))
  }

  function handleDeleted(id: number) {
    setPosts((current) => current.filter((p) => p.id !== id))
  }

  if (user === null) {
    return null
  }

  return (
    <>
      <header className="topbar">
        <h1>ホーム</h1>
        <span className="grow" />
        <span className="muted">
          {user.displayName}(@{user.username})
        </span>
        <button className="btn btn-outline" type="button" onClick={handleLogout} disabled={loggingOut}>
          ログアウト
        </button>
      </header>

      <PostComposer user={user} onPosted={(post) => setPosts((current) => [post, ...current])} />

      <section className="card" aria-label="タイムライン">
        {posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            mine={post.author.id === user.id}
            onUpdated={handleUpdated}
            onDeleted={handleDeleted}
          />
        ))}
        {!loading && error === null && posts.length === 0 && (
          <p className="empty">まだ投稿がありません。最初の投稿をしてみましょう</p>
        )}
        {error !== null && (
          <p className="form-error card-pad" role="alert">
            {error}
          </p>
        )}
        {loading && <span className="loading">読み込み中…</span>}
        {!loading && nextCursor !== null && (
          <div className="more">
            <button className="btn btn-outline" type="button" onClick={handleLoadMore}>
              もっと見る
            </button>
          </div>
        )}
      </section>
    </>
  )
}
