import { useEffect, useRef, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { deleteComment } from '../api/comments'
import type { Comment } from '../api/types'
import { relativeTime } from '../relativeTime'
import { Avatar } from './Avatar'

interface CommentItemProps {
  comment: Comment
  mine: boolean
  onDeleted: (id: number) => void
}

/** コメント1件。自分のコメントだけ、削除(確認つき)ができる。 */
export function CommentItem({ comment, mine, onDeleted }: CommentItemProps) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  // 削除確認はネイティブ <dialog>(フォーカストラップ・Esc で閉じる)
  useEffect(() => {
    if (confirming) {
      dialogRef.current?.showModal()
    }
  }, [confirming])

  function cancel() {
    setError(null)
    setConfirming(false)
  }

  async function handleDelete() {
    setBusy(true)
    setError(null)
    try {
      await deleteComment(comment.id)
      onDeleted(comment.id)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        onDeleted(comment.id) // 既に削除されている
        return
      }
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      setBusy(false)
    }
  }

  return (
    <article className="post">
      <Avatar user={comment.author} />
      <div className="post-main">
        <div className="post-head">
          <span className="name">{comment.author.displayName}</span>
          <span className="handle">@{comment.author.username}</span>
          <span className="dot">·</span>
          <span className="time">{relativeTime(comment.createdAt)}</span>
          <span className="grow" />
          {mine && !confirming && (
            <button className="btn-danger-text" type="button" onClick={() => setConfirming(true)}>
              削除
            </button>
          )}
        </div>
        <div className="post-body">{comment.body}</div>

        {confirming && (
          <dialog ref={dialogRef} className="confirm" aria-label="削除の確認" onCancel={cancel}>
            <p>このコメントを削除しますか?</p>
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
