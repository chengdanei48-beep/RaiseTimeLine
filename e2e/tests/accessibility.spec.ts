import AxeBuilder from '@axe-core/playwright'
import { test, expect } from '../fixtures/test.ts'

function seriousOrCritical(results: Awaited<ReturnType<AxeBuilder['analyze']>>) {
  return results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')
}

test.describe('アクセシビリティ(axe)', () => {
  test('ホーム画面に重大な違反がない', async ({ page, user }) => {
    void user
    const results = await new AxeBuilder({ page }).include('main').analyze()
    expect(seriousOrCritical(results)).toEqual([])
  })

  test('ログイン画面に重大な違反がない', async ({ page }) => {
    await page.goto('/login')
    const results = await new AxeBuilder({ page }).analyze()
    expect(seriousOrCritical(results)).toEqual([])
  })

  test('投稿モーダルに重大な違反がない。Escで閉じ、フォーカスが戻る', async ({ page, user }) => {
    void user
    const postButton = page.getByRole('button', { name: '投稿する' })
    await postButton.click()
    const results = await new AxeBuilder({ page }).include('dialog').analyze()
    expect(seriousOrCritical(results)).toEqual([])

    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog', { name: '投稿を作成' })).not.toBeVisible()
  })
})
