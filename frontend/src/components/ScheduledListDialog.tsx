import { useEffect, useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { deletePost, fetchScheduled } from '../api/posts'
import type { Post } from '../api/types'
import { formatDateTime } from '../relativeTime'
import { Modal } from './Modal'

interface ScheduledListDialogProps {
  onClose: () => void
}

/** 自分の予約中の投稿を一覧し、取り消せるモーダル(時刻の変更は取消して作り直す)。 */
export function ScheduledListDialog({ onClose }: ScheduledListDialogProps) {
  const [posts, setPosts] = useState<Post[] | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchScheduled()
      .then((items) => {
        if (!cancelled) {
          setPosts(items)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setPosts([])
          setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleCancel(id: number) {
    setBusyId(id)
    setError(null)
    try {
      await deletePost(id)
      setPosts((current) => (current ?? []).filter((p) => p.id !== id))
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        // 既に公開された・取り消された
        setPosts((current) => (current ?? []).filter((p) => p.id !== id))
      } else {
        setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      }
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Modal title="予約中の投稿" onClose={onClose}>
      {posts === null && <span className="loading">読み込み中…</span>}
      {posts !== null && posts.length === 0 && <p className="empty">予約中の投稿はありません</p>}
      {posts !== null && posts.length > 0 && (
        <ul className="scheduled-list">
          {posts.map((post) => (
            <li key={post.id}>
              <div className="scheduled-main">
                <div className="scheduled-time">{formatDateTime(post.createdAt)} に公開</div>
                <div className="post-body">{post.body}</div>
              </div>
              <button
                className="btn-danger-text"
                type="button"
                onClick={() => void handleCancel(post.id)}
                disabled={busyId === post.id}
              >
                取消
              </button>
            </li>
          ))}
        </ul>
      )}
      {error !== null && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="modal-actions">
        <button className="btn btn-outline" type="button" onClick={onClose}>
          閉じる
        </button>
      </div>
    </Modal>
  )
}
