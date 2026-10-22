import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import type { FollowUser } from '../api/types'
import { fetchFollowers, fetchFollowing, fetchProfile } from '../api/users'
import { BackButton } from '../components/BackButton'
import { UserRow } from '../components/UserRow'

export type FollowListKind = 'following' | 'followers'

type Status = 'loading' | 'ready' | 'notFound' | 'error'

/** S-08 フォロー中・フォロワー一覧。URL のユーザー・種類が変わったら、画面の状態を作り直す。 */
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const params = useParams()
  return <FollowListView key={`${kind}:${params.id}`} kind={kind} userId={Number(params.id)} />
}

function FollowListView({ kind, userId }: { kind: FollowListKind; userId: number }) {
  const validId = Number.isSafeInteger(userId) && userId >= 1
  const fetchList = kind === 'following' ? fetchFollowing : fetchFollowers

  const [loadStatus, setStatus] = useState<Status>('loading')
  const status: Status = validId ? loadStatus : 'notFound'
  const [displayName, setDisplayName] = useState('')
  const [users, setUsers] = useState<FollowUser[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!validId) {
      return
    }
    Promise.all([fetchProfile(userId), fetchList(userId, null)])
      .then(([profile, page]) => {
        if (cancelled) {
          return
        }
        setDisplayName(profile.displayName)
        setUsers(page.items)
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
  }, [userId, validId, fetchList])

  async function handleLoadMore() {
    if (nextCursor === null) {
      return
    }
    setLoadingMore(true)
    setMoreError(null)
    try {
      const page = await fetchList(userId, nextCursor)
      setUsers((current) => {
        const known = new Set(current.map((u) => u.id))
        return [...current, ...page.items.filter((u) => !known.has(u.id))]
      })
      setNextCursor(page.nextCursor)
    } catch (err) {
      setMoreError(err instanceof ApiError ? err.message : GENERIC_ERROR)
    } finally {
      setLoadingMore(false)
    }
  }

  function handleFollowChange(id: number, following: boolean) {
    setUsers((current) => current.map((u) => (u.id === id ? { ...u, followedByMe: following } : u)))
  }

  return (
    <>
      <header className="topbar">
        <BackButton />
        <h1>{displayName === '' ? 'フォロー' : displayName}</h1>
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

      {status === 'ready' && (
        <>
          <nav className="tabs" aria-label="フォロー">
            <Link
              to={`/users/${userId}/following`}
              className={kind === 'following' ? 'tab active' : 'tab'}
              aria-current={kind === 'following' ? 'page' : undefined}
              replace
            >
              フォロー中
            </Link>
            <Link
              to={`/users/${userId}/followers`}
              className={kind === 'followers' ? 'tab active' : 'tab'}
              aria-current={kind === 'followers' ? 'page' : undefined}
              replace
            >
              フォロワー
            </Link>
          </nav>

          <section className="card" aria-label={kind === 'following' ? 'フォロー中' : 'フォロワー'}>
            {users.map((u) => (
              <UserRow key={u.id} user={u} onFollowChange={handleFollowChange} />
            ))}
            {users.length === 0 && <p className="empty">まだいません</p>}
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
