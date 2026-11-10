import { test, expect } from '../fixtures/test.ts'

test.describe('セッション・ルーティング', () => {
  test('refresh Cookieが無いと保護画面はログインへ戻る', async ({ page, user, context }) => {
    void user
    await context.clearCookies()
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('API が 401、refresh も 401 ならセッション切れでログインへ戻る', async ({ page, user }) => {
    void user
    await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401, body: '{}' }))
    await page.route('**/api/timeline**', (route) => route.fulfill({ status: 401, body: '{}' }))
    await page.reload()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('未知のURLはリダイレクトされる', async ({ page, user }) => {
    void user
    await page.goto('/this-route-does-not-exist')
    await expect(page).toHaveURL(/\/$/)
  })
})
