import { test, expect } from '../fixtures/test.ts'

test.describe('レスポンシブ・ダークモード', () => {
  test('375px幅で横スクロールが出ない', async ({ page, user }) => {
    void user
    await page.setViewportSize({ width: 375, height: 800 })
    await page.reload()
    const { scrollWidth, clientWidth } = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1)
  })

  test('375px幅で投稿モーダルが画面内に収まる', async ({ page, user }) => {
    void user
    await page.setViewportSize({ width: 375, height: 800 })
    await page.getByRole('button', { name: '投稿する' }).click()
    const dialog = page.getByRole('dialog', { name: '投稿を作成' })
    const box = await dialog.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.x).toBeGreaterThanOrEqual(0)
    expect(box!.x + box!.width).toBeLessThanOrEqual(375 + 1)
  })

  test('ダークモードでも表示が崩れない', async ({ page, user }) => {
    void user
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.reload()
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  })
})
