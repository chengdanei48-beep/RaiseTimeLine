import type { User } from '../api/types'

const COLORS = ['#1d9bf0', '#7856ff', '#f91880', '#00ba7c', '#ff7a00', '#e0245e']

/** 画像機能(S3)の実装までは、表示名の頭文字を色付きの丸で表示する。 */
export function Avatar({ user }: { user: Pick<User, 'id' | 'displayName'> }) {
  return (
    <span className="avatar" style={{ background: COLORS[user.id % COLORS.length] }} aria-hidden="true">
      {Array.from(user.displayName)[0] ?? '?'}
    </span>
  )
}
