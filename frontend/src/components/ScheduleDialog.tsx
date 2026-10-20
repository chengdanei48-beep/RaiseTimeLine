import { useState } from 'react'
import type { SubmitEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { createPost } from '../api/posts'
import type { Post } from '../api/types'
import { formatDateTime } from '../relativeTime'
import { Modal } from './Modal'

/** 予約できるのは現在からこの日数先まで(バックエンドと同じ)。 */
const MAX_SCHEDULE_DAYS = 30

interface ScheduleDialogProps {
  body: string
  onClose: () => void
  onScheduled: (post: Post) => void
}

/** datetime-local の値(ローカル時刻 "YYYY-MM-DDTHH:mm")に変換する。 */
function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 次の「きりのいい時刻」(1時間後の0分)を初期値にする。 */
function defaultSchedule(): string {
  const d = new Date(Date.now() + 60 * 60 * 1000)
  d.setMinutes(0, 0, 0)
  return toLocalInput(d)
}

function validate(value: string): string | null {
  const at = new Date(value)
  if (value === '' || Number.isNaN(at.getTime())) {
    return '予約する日時を選んでください'
  }
  const now = Date.now()
  if (at.getTime() <= now) {
    return '予約日時は現在より後を指定してください'
  }
  if (at.getTime() > now + MAX_SCHEDULE_DAYS * 24 * 60 * 60 * 1000) {
    return `予約日時は${MAX_SCHEDULE_DAYS}日先までで指定してください`
  }
  return null
}

/** 投稿を予約する日時を選ぶモーダル。 */
export function ScheduleDialog({ body, onClose, onScheduled }: ScheduleDialogProps) {
  const [value, setValue] = useState(defaultSchedule)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const message = validate(value)
    if (message !== null) {
      setError(message)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const post = await createPost(body, new Date(value).toISOString())
      onScheduled(post)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      setSubmitting(false)
    }
  }

  const preview = validate(value) === null ? formatDateTime(new Date(value).toISOString()) : null

  return (
    <Modal title="投稿を予約" onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <p className="modal-quote">{body}</p>
        <div className="field">
          <label htmlFor="schedule-at">公開する日時(30日先まで)</label>
          <input
            id="schedule-at"
            type="datetime-local"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            aria-invalid={error !== null}
          />
          {preview !== null && <div className="hint">{preview} に公開されます</div>}
        </div>
        {error !== null && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="modal-actions">
          <button className="btn btn-outline" type="button" onClick={onClose} disabled={submitting}>
            キャンセル
          </button>
          <button className="btn" type="submit" disabled={submitting}>
            予約する
          </button>
        </div>
      </form>
    </Modal>
  )
}
