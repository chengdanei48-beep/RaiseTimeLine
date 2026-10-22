import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { createPost, fetchTimeline, updatePost } from '../api/posts'
import type { Post } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PostCard } from '../components/PostCard'
import { PostEditorModal } from '../components/PostEditorModal'
import { useToast } from '../components/ToastContext'

/** 開いている投稿モーダル。作成か、編集する投稿 */
type Editor = { kind: 'create' } | { kind: 'edit'; post: Post }

/** 新着を確認する間隔(ミリ秒) */
const POLL_INTERVAL_MS = 30_000

/** タイムライン API の 1 ページの件数。新着がこれ以上だと、ポーリングで全件は取れない */
const PAGE_SIZE = 20

/** S-03 タイムライン(全体。画像・フォロー中タブは後続) */
export function HomePage() {
  const { user, logout } = useAuth()
  const showToast = useToast()
  const [loggingOut, setLoggingOut] = useState(false)
  const [posts, setPosts] = useState<Post[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  /** ポーリングで見つけた、まだ表示していない新着 */
  const [pending, setPending] = useState<Post[]>([])
  const [editor, setEditor] = useState<Editor | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)
  /** 一覧を取得し直すたびに増やす。取り直し前に始めた続きの読み込みの結果を捨てるために使う */
  const generationRef = useRef(0)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const newestIdRef = useRef(0)

  // ID は投稿順に増えるので、表示中で最大の ID より大きいものが「新着」
  const newestId = posts.reduce((max, p) => Math.max(max, p.id), 0)
  const newPosts = pending.filter((p) => p.id > newestId)

  useEffect(() => {
    newestIdRef.current = newestId
  }, [newestId])

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
        .then((page) => setPending(page.items.filter((p) => p.id > newestIdRef.current)))
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
    const generation = generationRef.current
    setLoading(true)
    setError(null)
    try {
      const page = await fetchTimeline(nextCursor)
      if (generation !== generationRef.current) {
        return // 待っている間に一覧が取得し直された。古いカーソルの続きは使わない
      }
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

  async function showNewPosts() {
    if (newPosts.length >= PAGE_SIZE) {
      // 新着が 1 ページに収まらず、取れていない分が出る。先頭から取得し直して置き換える
      // (続きは無限スクロールで読み込む)
      setRefreshing(true)
      setRefreshError(null)
      try {
        const page = await fetchTimeline(null)
        generationRef.current += 1
        setPosts(page.items)
        setNextCursor(page.nextCursor)
        setError(null)
        setLoading(false)
        setPending([])
      } catch (err) {
        setRefreshError(err instanceof ApiError ? err.message : GENERIC_ERROR)
        return
      } finally {
        setRefreshing(false)
      }
    } else {
      setPosts((current) => {
        const known = new Set(current.map((p) => p.id))
        return [...newPosts.filter((p) => !known.has(p.id)), ...current]
      })
      setPending([])
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function handleCreate(body: string) {
    const post = await createPost(body)
    setPosts((current) => [post, ...current])
    setEditor(null)
    showToast('投稿しました')
  }

  async function handleEdit(target: Post, body: string) {
    try {
      const updated = await updatePost(target.id, body)
      setPosts((current) => current.map((p) => (p.id === updated.id ? updated : p)))
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 404)) {
        throw err
      }
      handleDeleted(target.id) // 既に削除されている
    }
    setEditor(null)
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
        <button className="btn" type="button" onClick={() => setEditor({ kind: 'create' })}>
          投稿する
        </button>
        <span className="muted">
          {user.displayName}(@{user.username})
        </span>
        <button className="btn btn-outline" type="button" onClick={handleLogout} disabled={loggingOut}>
          ログアウト
        </button>
      </header>

      {newPosts.length > 0 && (
        <>
          <button className="new-posts" type="button" onClick={() => void showNewPosts()} disabled={refreshing}>
            {newPosts.length >= PAGE_SIZE ? `${PAGE_SIZE}件以上の` : `${newPosts.length}件の`}新しい投稿を表示
          </button>
          {refreshError !== null && (
            <p className="form-error" role="alert">
              {refreshError}
            </p>
          )}
        </>
      )}

      <section className="card" aria-label="タイムライン">
        {posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            mine={post.author.id === user.id}
            onEdit={(target) => setEditor({ kind: 'edit', post: target })}
            onChange={(changed) =>
              setPosts((current) => current.map((p) => (p.id === changed.id ? changed : p)))
            }
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

      {editor?.kind === 'create' && (
        <PostEditorModal
          user={user}
          title="投稿を作成"
          submitLabel="投稿する"
          onSubmit={handleCreate}
          onClose={() => setEditor(null)}
        />
      )}
      {editor?.kind === 'edit' && (
        <PostEditorModal
          key={editor.post.id}
          user={user}
          title="投稿を編集"
          submitLabel="保存"
          initialBody={editor.post.body}
          onSubmit={(body) => handleEdit(editor.post, body)}
          onClose={() => setEditor(null)}
        />
      )}
    </>
  )
}
