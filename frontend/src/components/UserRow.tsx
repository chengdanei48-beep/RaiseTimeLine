import { Link } from 'react-router'
import type { FollowUser } from '../api/types'
import { Avatar } from './Avatar'
import { FollowButton } from './FollowButton'

interface UserRowProps {
  user: FollowUser
  /** フォロー状態が変わったとき */
  onFollowChange: (id: number, following: boolean) => void
}

/** フォロー中・フォロワー一覧の1行。アイコン・名前はプロフィールへのリンク。自分の行にはフォローボタンを出さない。 */
export function UserRow({ user, onFollowChange }: UserRowProps) {
  return (
    <div className="user-row">
      <Link to={`/users/${user.id}`} aria-label={`${user.displayName}のプロフィール`}>
        <Avatar user={user} />
      </Link>
      <div className="info">
        <Link className="name" to={`/users/${user.id}`}>
          {user.displayName}
        </Link>{' '}
        <span className="handle">@{user.username}</span>
        {user.bio !== null && user.bio !== '' && <div className="bio">{user.bio}</div>}
      </div>
      {user.followedByMe !== null && (
        <FollowButton
          userId={user.id}
          following={user.followedByMe}
          onChange={(following) => onFollowChange(user.id, following)}
        />
      )}
    </div>
  )
}
