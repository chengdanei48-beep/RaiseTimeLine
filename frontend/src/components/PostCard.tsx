import { useEffect, useRef, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { Link } from 'react-router'
import { deletePost, fetchPost, likePost, unlikePost } from '../api/posts'
import type { Post } from '../api/types'
import { relativeTime } from '../relativeTime'
import { Avatar } from './Avatar'
import { PostImages } from './PostImages'
import { useToast } from './ToastContext'

interface PostCardProps {
  post: Post
  mine: boolean
  /** 投稿詳細での表示(本文を大きくする) */
  detail?: boolean
  /** 省略すると編集ボタンを出さない(投稿詳細) */
  onEdit?: (post: Post) => void
  /** いいねの操作で投稿の表示内容が変わったときに、新しい内容を渡す */
  onChange: (post: Post) => void
  onDeleted: (id: number) => void
}

/** 画像の読み込み失敗で投稿を再取得する最短の間隔(ミリ秒)。本当に壊れた画像で再取得を繰り返さないため */
const IMAGE_REFRESH_MIN_INTERVAL_MS = 30_000

type Mode = 'view' | 'confirmDelete'

function describe(err: unknown): string {
  return err instanceof ApiError ? err.message : GENERIC_ERROR
}

/** 投稿1件。自分の投稿だけ、編集(モーダルを開く)と削除(確認つき)ができる。 */
export function PostCard({ post, mine, detail = false, onEdit, onChange, onDeleted }: PostCardProps) {
  const showToast = useToast()
  const [mode, setMode] = useState<Mode>('view')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [liking, setLiking] = useState(false)
  const [likeError, setLikeError] = useState<string | null>(null)
  const refreshingImagesRef = useRef(false)
  const lastImageRefreshRef = useRef(0)

  const dialogRef = useRef<HTMLDialogElement>(null)

  // 削除確認はネイティブ <dialog>(フォーカストラップ・Esc で閉じる)
  useEffect(() => {
    if (mode === 'confirmDelete') {
      dialogRef.current?.showModal()
    }
  }, [mode])

  const edited = new Date(post.updatedAt).getTime() > new Date(post.createdAt).getTime()

  function cancel() {
    setError(null)
    setMode('view')
  }

  /**
   * 画像の読み込みに失敗したら(署名付き URL の期限切れなど)、投稿を取得し直して新しい URL に差し替える。
   * 画像が複数枚同時に失敗しても 1 回だけ。続けて失敗しても、一定時間は再取得しない(壊れた画像での無限ループを避ける)。
   * 失敗しても何もしない(画像は表示されないままになる)。
   */
  async function refreshImages() {
    const now = Date.now()
    if (
      refreshingImagesRef.current ||
      liking ||
      now - lastImageRefreshRef.current < IMAGE_REFRESH_MIN_INTERVAL_MS
    ) {
      return
    }
    refreshingImagesRef.current = true
    lastImageRefreshRef.current = now
    try {
      onChange(await fetchPost(post.id))
    } catch {
      // 投稿が削除された・通信に失敗した場合は、そのまま(次の機会に任せる)
    } finally {
      refreshingImagesRef.current = false
    }
  }

  /** いいねのトグル。件数は先に反映し(楽観的更新)、失敗したら元に戻す。連打は処理中のボタン無効化で防ぐ */
  async function handleToggleLike() {
    const original = post
    const liked = !original.likedByMe
    setLiking(true)
    setLikeError(null)
    onChange({ ...original, likedByMe: liked, likeCount: original.likeCount + (liked ? 1 : -1) })
    try {
      await (liked ? likePost(original.id) : unlikePost(original.id))
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onDeleted(original.id) // 既に削除されている
        return
      }
      onChange(original)
      setLikeError(describe(err))
    } finally {
      setLiking(false)
    }
  }

  async function handleDelete() {
    setBusy(true)
    setError(null)
    try {
      await deletePost(post.id)
      showToast('投稿を削除しました')
      onDeleted(post.id)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onDeleted(post.id) // 既に削除されている
        return
      }
      setError(describe(err))
      setBusy(false)
    }
  }

  return (
    <article className={detail ? 'post detail' : 'post'}>
      <Link to={`/users/${post.author.id}`} aria-label={`${post.author.displayName}のプロフィール`}>
        <Avatar user={post.author} />
      </Link>
      <div className="post-main">
        <div className="post-head">
          <Link className="name" to={`/users/${post.author.id}`}>
            {post.author.displayName}
          </Link>
          <Link className="handle" to={`/users/${post.author.id}`}>
            @{post.author.username}
          </Link>
          <span className="dot">·</span>
          <Link className="time" to={`/posts/${post.id}`}>
            {relativeTime(post.createdAt)}
          </Link>
          {edited && <span className="edited">(編集済み)</span>}
          <span className="grow" />
          {mine && mode === 'view' && (
            <>
              {onEdit !== undefined && (
                <button className="btn-text" type="button" onClick={() => onEdit(post)}>
                  編集
                </button>
              )}
              <button className="btn-danger-text" type="button" onClick={() => setMode('confirmDelete')}>
                削除
              </button>
            </>
          )}
        </div>

        <div className="post-body">{post.body}</div>
        {post.images.length > 0 && <PostImages images={post.images} onLoadError={() => void refreshImages()} />}

        <div className="post-actions">
          <Link className="action" to={`/posts/${post.id}`} aria-label={`コメント ${post.commentCount}件`}>
            <span aria-hidden="true">💬</span> {post.commentCount}
          </Link>
          <button
            className={post.likedByMe ? 'action like liked' : 'action like'}
            type="button"
            aria-pressed={post.likedByMe}
            aria-label={`いいね ${post.likeCount}件`}
            disabled={liking}
            onClick={() => void handleToggleLike()}
          >
            <span aria-hidden="true">{post.likedByMe ? '♥' : '♡'}</span> {post.likeCount}
          </button>
        </div>
        {likeError !== null && (
          <p className="form-error" role="alert">
            {likeError}
          </p>
        )}

        {mode === 'confirmDelete' && (
          <dialog ref={dialogRef} className="confirm" aria-label="削除の確認" onCancel={cancel}>
            <p>この投稿を削除しますか?(コメント・いいね・画像も見られなくなります)</p>
            <div className="confirm-actions">
              <button className="btn btn-outline" type="button" onClick={cancel} disabled={busy}>
                キャンセル
              </button>
              <button className="btn btn-danger" type="button" onClick={handleDelete} disabled={busy}>
                削除する
              </button>
            </div>
            {error !== null && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
          </dialog>
        )}
      </div>
    </article>
  )
}
