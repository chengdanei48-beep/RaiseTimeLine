import { useEffect, useState } from 'react'
import type { SubmitEvent } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import type { FollowUser } from '../api/types'
import { searchUsers } from '../api/users'
import { BackButton } from '../components/BackButton'
import { UserRow } from '../components/UserRow'
import { normalizeSearchKeyword, SEARCH_MAX_LENGTH } from '../validation'

/** S-07 ユーザー検索。検索語は URL(?q=)に持つので、戻る・再読み込みで復元される。 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const keyword = normalizeSearchKeyword(q)

  function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const value = new FormData(e.currentTarget).get('q')
    setParams({ q: typeof value === 'string' ? value.trim() : '' })
  }

  const tooLong = keyword.length > SEARCH_MAX_LENGTH

  return (
    <>
      <header className="topbar">
        <BackButton />
        <h1>ユーザー検索</h1>
      </header>

      {/* 戻る操作などで URL の検索語が変わったら、入力欄も作り直して合わせる */}
      <search className="card">
        <form key={q} className="search-box" onSubmit={handleSubmit}>
          <input
            type="search"
            name="q"
            aria-label="ユーザーIDまたは表示名"
            aria-invalid={tooLong ? true : undefined}
            aria-describedby={tooLong ? 'search-error' : undefined}
            placeholder="ユーザーIDまたは表示名で検索"
            defaultValue={q}
          />
          <button className="btn" type="submit">
            検索
          </button>
        </form>
      </search>

      {keyword === '' && <p className="empty">ユーザーIDまたは表示名を入力してください</p>}
      {tooLong && (
        <p id="search-error" className="form-error" role="alert">
          検索語は{SEARCH_MAX_LENGTH}文字以内で入力してください
        </p>
      )}
      {keyword !== '' && !tooLong && <SearchResults key={keyword} keyword={keyword} />}
    </>
  )
}

type Status = 'loading' | 'ready' | 'error'

/** 検索結果。検索語が変わったら作り直される(key)。 */
function SearchResults({ keyword }: { keyword: string }) {
  const [status, setStatus] = useState<Status>('loading')
  const [errorMessage, setErrorMessage] = useState(GENERIC_ERROR)
  const [users, setUsers] = useState<FollowUser[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    searchUsers(keyword, null)
      .then((page) => {
        if (cancelled) {
          return
        }
        setUsers(page.items)
        setNextCursor(page.nextCursor)
        setStatus('ready')
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setErrorMessage(err instanceof ApiError ? err.message : GENERIC_ERROR)
          setStatus('error')
        }
      })
    return () => {
      cancelled = true
    }
  }, [keyword])

  async function handleLoadMore() {
    if (nextCursor === null) {
      return
    }
    setLoadingMore(true)
    setMoreError(null)
    try {
      const page = await searchUsers(keyword, nextCursor)
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

  if (status === 'loading') {
    return <span className="loading">検索中…</span>
  }
  if (status === 'error') {
    return (
      <p className="form-error" role="alert">
        {errorMessage}
      </p>
    )
  }

  return (
    <section className="card" aria-label="検索結果">
      {users.map((u) => (
        <UserRow key={u.id} user={u} onFollowChange={handleFollowChange} />
      ))}
      {users.length === 0 && <p className="empty">該当するユーザーが見つかりません</p>}
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
  )
}
