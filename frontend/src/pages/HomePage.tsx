import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { fetchTimeline } from '../api/posts'
import type { Post } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PostCard } from '../components/PostCard'
import { PostComposer } from '../components/PostComposer'

/** 新着を確認する間隔(ミリ秒) */
const POLL_INTERVAL_MS = 30_000

/** S-03 タイムライン(全体。画像・フォロー中タブ・いいね/コメント数は後続) */
export function HomePage() {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)
  const [posts, setPosts] = useState<Post[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  /** ポーリングで見つけた、まだ表示していない新着 */
  const [pending, setPending] = useState<Post[]>([])
  const sentinelRef = useRef<HTMLDivElement>(null)
  const newestRef = useRef(0)

  // 並び順は公開日時(createdAt)なので、表示中で最新の公開日時より後で、未表示のものが「新着」
  // (予約投稿は ID が古いまま公開されるため、ID では判定できない)
  const newestAt = posts.reduce((max, p) => Math.max(max, Date.parse(p.createdAt)), 0)
  const shownIds = new Set(posts.map((p) => p.id))
  const newPosts = pending.filter((p) => Date.parse(p.createdAt) > newestAt && !shownIds.has(p.id))

  useEffect(() => {
    newestRef.current = newestAt
  }, [newestAt])

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

  // 新着のポーリング。タブが見えていない間は問い合わせない
  useEffect(() => {
    const timer = setInterval(() => {
      if (document.hidden) {
        return
      }
      fetchTimeline(null)
        .then((page) => setPending(page.items.filter((p) => Date.parse(p.createdAt) > newestRef.current)))
        .catch(() => {
          // 一時的な失敗は黙って次の周期に任せる
        })
    }, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [])

  const handleLoadMore = useCallback(async () => {
    if (nextCursor === null) {
      return
    }
    setLoading(true)
    setError(null)
    try {
      const page = await fetchTimeline(nextCursor)
      setPosts((current) => {
        const known = new Set(current.map((p) => p.id))
        return [...current, ...page.items.filter((p) => !known.has(p.id))]
      })
      setNextCursor(page.nextCursor)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setLoading(false)
    }
  }, [nextCursor])

  // 無限スクロール: 末尾の目印が見えたら次ページを読み込む。
  // 読み込み後も見えたままなら監視し直して続けて読む。失敗時は止めて「もっと見る」で再試行させる
  const canAutoLoad = nextCursor !== null && !loading && error === null
  useEffect(() => {
    const target = sentinelRef.current
    if (!canAutoLoad || target === null) {
      return
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          void handleLoadMore()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(target)
    return () => observer.disconnect()
  }, [canAutoLoad, handleLoadMore])

  async function handleLogout() {
    setLoggingOut(true)
    try {
      // 成功・失敗にかかわらずログアウト状態になり、RequireAuth が S-02 へ移動させる
      await logout()
    } catch {
      // サーバーへの通知に失敗してもブラウザ側のトークンは破棄済み
    }
  }

  function showNewPosts() {
    setPosts((current) => {
      const known = new Set(current.map((p) => p.id))
      return [...newPosts.filter((p) => !known.has(p.id)), ...current]
    })
    setPending([])
    window.scrollTo({ top: 0, behavior: 'smooth' })
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

      {newPosts.length > 0 && (
        <button className="new-posts" type="button" onClick={showNewPosts}>
          {newPosts.length >= 20 ? '20件以上の' : `${newPosts.length}件の`}新しい投稿を表示
        </button>
      )}

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
        {nextCursor !== null && <div ref={sentinelRef} aria-hidden="true" />}
        {!loading && nextCursor !== null && error !== null && (
          <div className="more">
            <button className="btn btn-outline" type="button" onClick={() => void handleLoadMore()}>
              もっと見る
            </button>
          </div>
        )}
      </section>
    </>
  )
}
