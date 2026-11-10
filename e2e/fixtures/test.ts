import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test'
import { makeUser, DEFAULT_PASSWORD, type TestUser } from './data.ts'

interface AuthResponse {
  accessToken: string
  user: { id: number; username: string; displayName: string }
}

/** API で登録・ログインする(UI を毎回通さない)。Cookie はこの request context に残る。 */
async function registerAndLogin(request: APIRequestContext, user: TestUser): Promise<AuthResponse> {
  await request.post('/api/auth/register', {
    data: { username: user.username, displayName: user.displayName, password: user.password },
  })
  const res = await request.post('/api/auth/login', {
    data: { username: user.username, password: user.password },
  })
  if (!res.ok()) {
    throw new Error(`login failed for ${user.username}: ${res.status()} ${await res.text()}`)
  }
  return (await res.json()) as AuthResponse
}

/**
 * ログイン済みページを開く。refresh トークンの Cookie は `page.request` のコンテキストに
 *登録時点で入っているので、`/` を開くと AuthProvider が自動で refresh してセッションを復元する。
 * (アクセストークンはメモリ保持のため、Cookie 経由のこの方法でのみ「ログイン済み」を再現できる)
 */
async function gotoAsLoggedIn(page: Page, user: TestUser): Promise<AuthResponse> {
  const auth = await registerAndLogin(page.request, user)
  user.id = auth.user.id
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  return auth
}

/**
 * 未捕捉例外(JS のクラッシュ)を検知して、テスト終了時に失敗させる。
 * console.error や 4xx/5xx 応答は、401/404/429/500 を意図的に再現する障害系テストで
 * 普通に発生するため(ブラウザが失敗したリクエストを自動で console.error に出す)、
 * ここでは対象にしない。本当の不具合は pageerror(未捕捉の例外)で捉える。
 */
function watchForUnexpectedErrors(page: Page, problems: string[]): void {
  page.on('pageerror', (err) => {
    problems.push(`pageerror: ${err.message}`)
  })
}

type Fixtures = {
  /** 自動で登録・ログイン済みの 1 人目のユーザーとページ */
  user: TestUser
  /** 2 人目のユーザー(新着バナー・いいね反映など、複数ユーザーの同時操作用)。別の BrowserContext・Page を持つ */
  secondUser: { user: TestUser; page: Page }
}

export const test = base.extend<Fixtures>({
  page: async ({ page }, use) => {
    const problems: string[] = []
    watchForUnexpectedErrors(page, problems)
    await use(page)
    expect(problems, '想定外のコンソールエラー・例外・5xx応答が発生した').toEqual([])
  },

  user: async ({ page }, use) => {
    const user = makeUser('u1')
    await gotoAsLoggedIn(page, user)
    await use(user)
  },

  secondUser: async ({ browser }, use) => {
    const context = await browser.newContext({ locale: 'ja-JP' })
    const page = await context.newPage()
    const problems: string[] = []
    watchForUnexpectedErrors(page, problems)
    const user = makeUser('u2')
    await gotoAsLoggedIn(page, user)
    await use({ user, page })
    expect(problems, '2人目: 想定外のコンソールエラー・例外・5xx応答が発生した').toEqual([])
    await context.close()
  },
})

export { expect, DEFAULT_PASSWORD }
