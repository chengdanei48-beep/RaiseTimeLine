import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import type { Post, Profile } from '../api/types'
import { fetchProfile, fetchUserPosts } from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { BackButton } from '../components/BackButton'
import { FollowButton } from '../components/FollowButton'
import { PostCard } from '../components/PostCard'

type Status = 'loading' | 'ready' | 'notFound' | 'error'

/** S-05 プロフィール。URL のユーザーが変わったら、画面の状態を作り直す。 */
export function ProfilePage() {
  const params = useParams()
  return <ProfileView key={params.id} userId={Number(params.id)} />
}

function ProfileView({ userId }: { userId: number }) {
  const { user } = useAuth()
  const validId = Number.isSafeInteger(userId) && userId >= 1

  const [loadStatus, setStatus] = useState<Status>('loading')
  const status: Status = validId ? loadStatus : 'notFound'
  const [profile, setProfile] = useState<Profile | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!validId) {
      return
    }
    Promise.all([fetchProfile(userId), fetchUserPosts(userId, null)])
      .then(([loadedProfile, page]) => {
        if (cancelled) {
          return
        }
        setProfile(loadedProfile)
        setPosts(page.items)
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
  }, [userId, validId])

  async function handleLoadMore() {
    if (nextCursor === null) {
      return
    }
    setLoadingMore(true)
    setMoreError(null)
    try {
      const page = await fetchUserPosts(userId, nextCursor)
      setPosts((current) => {
        const known = new Set(current.map((p) => p.id))
        return [...current, ...page.items.filter((p) => !known.has(p.id))]
      })
      setNextCursor(page.nextCursor)
    } catch (err) {
      setMoreError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setLoadingMore(false)
    }
  }

  /** フォロー状態が変わったら、フォロワー数も同じだけ動かす(押した直後と、失敗して戻すとき) */
  function handleFollowChange(following: boolean) {
    setProfile((current) => {
      if (current === null || current.followedByMe === following) {
        return current
      }
      return {
        ...current,
        followedByMe: following,
        followerCount: current.followerCount + (following ? 1 : -1),
      }
    })
  }

  function handlePostDeleted(id: number) {
    setPosts((current) => current.filter((p) => p.id !== id))
    setProfile((current) =>
      current === null ? current : { ...current, postCount: Math.max(0, current.postCount - 1) },
    )
  }

  if (user === null) {
    return null
  }

  return (
    <>
      <header className="topbar">
        <BackButton />
        <h1>プロフィール</h1>
      </header>

      {status === 'loading' && <span className="loading">読み込み中…</span>}
      {status === 'error' && (
        <p className="form-error" role="alert">
          {GENERIC_ERROR}
        </p>
      )}
      {status === 'notFound' && (
        <section className="card">
          <p className="empty">ユーザーが見つかりません</p>
          <div className="more">
            <Link className="btn btn-outline" to="/">
              タイムラインへ戻る
            </Link>
          </div>
        </section>
      )}

      {status === 'ready' && profile !== null && (
        <>
          <section className="card profile-top" aria-label="プロフィール">
            <div className="profile-row">
              <Avatar user={profile} size="lg" />
              <div className="profile-info">
                <h2>{profile.displayName}</h2>
                <span className="muted">@{profile.username}</span>
              </div>
              {!profile.isMe && (
                <FollowButton
                  userId={profile.id}
                  following={profile.followedByMe}
                  onChange={handleFollowChange}
                />
              )}
            </div>
            {profile.bio !== null && profile.bio !== '' && <p className="profile-bio">{profile.bio}</p>}
            <div className="counts">
              <span>
                <b>{profile.postCount}</b> 投稿
              </span>
              <Link to={`/users/${profile.id}/following`}>
                <b>{profile.followingCount}</b> フォロー中
              </Link>
              <Link to={`/users/${profile.id}/followers`}>
                <b>{profile.followerCount}</b> フォロワー
              </Link>
            </div>
          </section>

          <section className="card" aria-label="投稿">
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                mine={post.author.id === user.id}
                onChange={(changed) =>
                  setPosts((current) => current.map((p) => (p.id === changed.id ? changed : p)))
                }
                onDeleted={handlePostDeleted}
              />
            ))}
            {posts.length === 0 && <p className="empty">まだ投稿がありません</p>}
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
