import { useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { followUser, unfollowUser } from '../api/users'

interface FollowButtonProps {
  userId: number
  following: boolean
  /** フォロー状態が変わったとき(押した直後と、失敗して元に戻すとき)に新しい状態を渡す */
  onChange: (following: boolean) => void
}

/** フォロー/解除ボタン(共通部品)。状態は先に反映し(楽観的更新)、失敗したら元に戻す。解除に確認は出さない。 */
export function FollowButton({ userId, following, onChange }: FollowButtonProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleClick() {
    const original = following
    const next = !original
    setBusy(true)
    setError(null)
    onChange(next)
    try {
      await (next ? followUser(userId) : unfollowUser(userId))
    } catch (err) {
      onChange(original)
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="follow-wrap">
      <button
        className={following ? 'btn btn-outline' : 'btn'}
        type="button"
        aria-pressed={following}
        disabled={busy}
        onClick={() => void handleClick()}
      >
        {following ? 'フォロー中 ✓' : 'フォローする'}
      </button>
      {error !== null && (
        <span className="form-error" role="alert">
          {error}
        </span>
      )}
    </span>
  )
}
