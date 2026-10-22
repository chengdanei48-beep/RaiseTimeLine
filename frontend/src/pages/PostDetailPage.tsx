import { useEffect, useState } from 'react'
import type { SubmitEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { BackButton } from '../components/BackButton'
import { createComment, fetchComments } from '../api/comments'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { fetchPost } from '../api/posts'
import type { Comment, Post } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CommentItem } from '../components/CommentItem'
import { PostCard } from '../components/PostCard'
import { POST_MAX_LENGTH, validatePostBody } from '../validation'

type Status = 'loading' | 'ready' | 'notFound' | 'error'

/** S-04 投稿詳細。投稿1件とコメント(古い順)、コメントの入力。 */
export function PostDetailPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const params = useParams()
  const postId = Number(params.id)
  const validId = Number.isSafeInteger(postId) && postId >= 1

  const [loadStatus, setStatus] = useState<Status>('loading')
  const status: Status = validId ? loadStatus : 'notFound'
  const [post, setPost] = useState<Post | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState<string | null>(null)
  const [body, setBody] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!validId) {
      return
    }
    Promise.all([fetchPost(postId), fetchComments(postId, null)])
      .then(([loadedPost, page]) => {
        if (cancelled) {
          return
        }
        setPost(loadedPost)
        setComments(page.items)
        setNextCursor(page.nextCursor)
        setStatus('ready')
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setStatus(err instanceof ApiError && err.status === 404 ? 'notFound' : 'error')
        }
      })
    return () => {
      cancelled = true
    }
  }, [postId, validId])

  async function handleLoadMore() {
    if (nextCursor === null) {
      return
    }
    setLoadingMore(true)
    setMoreError(null)
    try {
      const page = await fetchComments(postId, nextCursor)
      setComments((current) => {
        const known = new Set(current.map((c) => c.id))
        return [...current, ...page.items.filter((c) => !known.has(c.id))]
      })
      setNextCursor(page.nextCursor)
    } catch (err) {
      setMoreError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setLoadingMore(false)
    }
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const message = validatePostBody(body)
    if (message !== null) {
      setSubmitError(message)
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await createComment(postId, body.trim())
      // 一覧の末尾に追加し、コメント数を +1 する。入力は成功したときだけ空にする
      setComments((current) => [...current, created])
      setPost((current) => (current === null ? current : { ...current, commentCount: current.commentCount + 1 }))
      setBody('')
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setStatus('notFound') // 投稿が削除された
        return
      }
      setSubmitError(err instanceof ApiError ? (err.errors.body ?? err.message) : GENERIC_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  function handleCommentDeleted(id: number) {
    setComments((current) => current.filter((c) => c.id !== id))
    setPost((current) =>
      current === null ? current : { ...current, commentCount: Math.max(0, current.commentCount - 1) },
    )
  }

  if (user === null) {
    return null
  }

  const remaining = POST_MAX_LENGTH - body.trim().length
  const invalid = validatePostBody(body) !== null

  return (
    <>
      <header className="topbar">
        <BackButton />
        <h1>投稿</h1>
      </header>

      {status === 'loading' && <span className="loading">読み込み中…</span>}
      {status === 'error' && (
        <p className="form-error" role="alert">
          {GENERIC_ERROR}
        </p>
      )}
      {status === 'notFound' && (
        <section className="card">
          <p className="empty">投稿が見つかりません</p>
          <div className="more">
            <Link className="btn btn-outline" to="/">
              タイムラインへ戻る
            </Link>
          </div>
        </section>
      )}

      {status === 'ready' && post !== null && (
        <>
          <section className="card" aria-label="投稿">
            <PostCard
              post={post}
              mine={post.author.id === user.id}
              detail
              onChange={setPost}
              onDeleted={() => navigate('/')}
            />
          </section>

          <section className="card" aria-label="コメントの入力">
            <form className="comment-form" onSubmit={handleSubmit} noValidate>
              <textarea
                aria-label="コメント"
                aria-invalid={submitError !== null ? true : undefined}
                aria-describedby={submitError !== null ? 'comment-error' : undefined}
                placeholder="コメントを入力(280字)"
                value={body}
                onChange={(e) => setBody(e.target.value)}
              />
              <div className="comment-foot">
                <span className={remaining < 0 ? 'counter over' : 'counter'}>残り {remaining}</span>
                <button className="btn" type="submit" disabled={submitting || invalid}>
                  返信する
                </button>
              </div>
              {submitError !== null && (
                <p id="comment-error" className="form-error" role="alert">
                  {submitError}
                </p>
              )}
            </form>
          </section>

          <section className="card" aria-label="コメント一覧">
            {comments.map((comment) => (
              <CommentItem
                key={comment.id}
                comment={comment}
                mine={comment.author.id === user.id}
                onDeleted={handleCommentDeleted}
              />
            ))}
            {comments.length === 0 && <p className="empty">まだコメントがありません</p>}
            {moreError !== null && (
              <p className="form-error card-pad" role="alert">
                {moreError}
              </p>
            )}
            {nextCursor !== null && (
              <div className="more">
                <button
                  className="btn btn-outline"
                  type="button"
                  onClick={() => void handleLoadMore()}
                  disabled={loadingMore}
                >
                  もっと見る
                </button>
              </div>
            )}
          </section>
        </>
      )}
    </>
  )
}
