import { useEffect, useRef, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { deletePost, updatePost } from '../api/posts'
import type { Post } from '../api/types'
import { relativeTime } from '../relativeTime'
import { POST_MAX_LENGTH, validatePostBody } from '../validation'
import { Avatar } from './Avatar'

interface PostCardProps {
  post: Post
  mine: boolean
  onUpdated: (post: Post) => void
  onDeleted: (id: number) => void
}

type Mode = 'view' | 'edit' | 'confirmDelete'

function describe(err: unknown): string {
  if (err instanceof ApiError) {
    return err.errors.body ?? err.message
  }
  return GENERIC_ERROR
}

/** 投稿1件。自分の投稿だけ、編集(インライン)と削除(確認つき)ができる。 */
export function PostCard({ post, mine, onUpdated, onDeleted }: PostCardProps) {
  const [mode, setMode] = useState<Mode>('view')
  const [draft, setDraft] = useState(post.body)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dialogRef = useRef<HTMLDialogElement>(null)

  // 削除確認はネイティブ <dialog>(フォーカストラップ・Esc で閉じる)
  useEffect(() => {
    if (mode === 'confirmDelete') {
      dialogRef.current?.showModal()
    }
  }, [mode])

  const edited = new Date(post.updatedAt).getTime() > new Date(post.createdAt).getTime()
  const remaining = POST_MAX_LENGTH - draft.trim().length

  function startEdit() {
    setDraft(post.body)
    setError(null)
    setMode('edit')
  }

  function cancel() {
    setError(null)
    setMode('view')
  }

  async function handleSave() {
    const message = validatePostBody(draft)
    if (message !== null) {
      setError(message)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const updated = await updatePost(post.id, draft.trim())
      onUpdated(updated)
      setMode('view')
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onDeleted(post.id) // 既に削除されている
        return
      }
      setError(describe(err))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    setBusy(true)
    setError(null)
    try {
      await deletePost(post.id)
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
    <article className="post">
      <Avatar user={post.author} />
      <div className="post-main">
        <div className="post-head">
          <span className="name">{post.author.displayName}</span>
          <span className="handle">@{post.author.username}</span>
          <span className="dot">·</span>
          <span className="time">{relativeTime(post.createdAt)}</span>
          {edited && <span className="edited">(編集済み)</span>}
          <span className="grow" />
          {mine && mode === 'view' && (
            <>
              <button className="btn-text" type="button" onClick={startEdit}>
                編集
              </button>
              <button className="btn-danger-text" type="button" onClick={() => setMode('confirmDelete')}>
                削除
              </button>
            </>
          )}
        </div>

        {mode === 'edit' ? (
          <div className="post-edit">
            <textarea aria-label="本文を編集" value={draft} onChange={(e) => setDraft(e.target.value)} />
            <div className="post-edit-foot">
              <span className={remaining < 0 ? 'counter over' : 'counter'}>残り {remaining}</span>
              <span className="grow" />
              <button className="btn btn-outline" type="button" onClick={cancel} disabled={busy}>
                キャンセル
              </button>
              <button
                className="btn"
                type="button"
                onClick={handleSave}
                disabled={busy || validatePostBody(draft) !== null}
              >
                保存
              </button>
            </div>
          </div>
        ) : (
          <div className="post-body">{post.body}</div>
        )}

        {mode === 'confirmDelete' && (
          <dialog ref={dialogRef} className="confirm" aria-label="削除の確認" onCancel={cancel}>
            <p>この投稿を削除しますか?</p>
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
        {error !== null && mode !== 'confirmDelete' && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </article>
  )
}
