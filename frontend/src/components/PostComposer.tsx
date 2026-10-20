import { useState } from 'react'
import type { SubmitEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { createPost } from '../api/posts'
import type { Post, User } from '../api/types'
import { formatDateTime } from '../relativeTime'
import { POST_MAX_LENGTH, validatePostBody } from '../validation'
import { Avatar } from './Avatar'
import { ScheduleDialog } from './ScheduleDialog'
import { ScheduledListDialog } from './ScheduledListDialog'

interface PostComposerProps {
  user: User
  onPosted: (post: Post) => void
}

type Dialog = 'schedule' | 'scheduled-list' | null

/** 投稿フォーム。残り文字数を表示し、成功したら onPosted で一覧の先頭へ追加させる。予約はモーダルで行う。 */
export function PostComposer({ user, onPosted }: PostComposerProps) {
  const [body, setBody] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialog>(null)

  const remaining = POST_MAX_LENGTH - body.trim().length
  const invalid = validatePostBody(body) !== null

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const message = validatePostBody(body)
    if (message !== null) {
      setError(message)
      return
    }
    setSubmitting(true)
    setError(null)
    setNotice(null)
    try {
      const post = await createPost(body.trim())
      setBody('')
      onPosted(post)
    } catch (err) {
      setError(err instanceof ApiError ? (err.errors.body ?? err.message) : GENERIC_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  function handleScheduled(post: Post) {
    setDialog(null)
    setBody('')
    setError(null)
    setNotice(`${formatDateTime(post.createdAt)} に公開するよう予約しました`)
  }

  return (
    <>
      <form className="card card-pad compose" onSubmit={handleSubmit} noValidate>
        <div className="compose-row">
          <Avatar user={user} />
          <textarea
            aria-label="本文"
            placeholder="いまどうしてる?"
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
        </div>
        {error !== null && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {notice !== null && (
          <output className="form-notice">{notice}</output>
        )}
        <div className="compose-foot">
          <button className="btn-text" type="button" onClick={() => setDialog('scheduled-list')}>
            予約中の投稿
          </button>
          <span className="grow" />
          <span className={remaining < 0 ? 'counter over' : 'counter'}>残り {remaining}</span>
          <button
            className="btn btn-outline"
            type="button"
            onClick={() => setDialog('schedule')}
            disabled={submitting || invalid}
          >
            予約
          </button>
          <button className="btn" type="submit" disabled={submitting || invalid}>
            投稿する
          </button>
        </div>
      </form>

      {dialog === 'schedule' && (
        <ScheduleDialog body={body.trim()} onClose={() => setDialog(null)} onScheduled={handleScheduled} />
      )}
      {dialog === 'scheduled-list' && <ScheduledListDialog onClose={() => setDialog(null)} />}
    </>
  )
}
