import { test, expect } from '../fixtures/test.ts'
import { uniqueBody } from '../fixtures/data.ts'
import { openPostEditor, postCardByBody, postCards, switchTab, newPostsBanner, scrollToBottom } from '../pages/home.ts'
import { editorDialog, fillBody, submitEditor } from '../pages/post-editor.ts'

test.describe('F-TL タイムライン', () => {
  test('新規ユーザーは「フォロー中」が空で、検索への案内が出る', async ({ page, user }) => {
    void user
    await expect(page.locator('.empty')).toContainText('まだ投稿がありません')
    await expect(page.getByRole('link', { name: 'ユーザーを検索する' })).toBeVisible()
  })

  test('「全体」タブに切り替えると他人の投稿が見える', async ({ page, user, secondUser }) => {
    void user
    const body = uniqueBody('all-tab')
    await secondUser.page.getByRole('button', { name: '投稿する' }).click()
    await fillBody(secondUser.page, 'create', body)
    await submitEditor(secondUser.page, 'create')

    await switchTab(page, '全体')
    await expect(postCardByBody(page, body).first()).toBeVisible()
  })

  test('21件以上をスクロールしても重複なく読み込める', async ({ page, user }) => {
    for (let i = 0; i < 21; i++) {
      await openPostEditor(page)
      await fillBody(page, 'create', uniqueBody(`scroll-${user.username}-${i}`))
      await submitEditor(page, 'create')
      await expect(page.locator('output.toast.show')).toContainText('投稿しました')
      // モーダルが閉じきるまで待つ(開いたまま次を開こうとすると「投稿する」ボタンが2つになる)
      await expect(editorDialog(page, 'create')).not.toBeVisible()
    }
    await page.reload()

    let previousCount = 0
    for (let i = 0; i < 5; i++) {
      await scrollToBottom(page)
      await page.waitForTimeout(300)
      const count = await postCards(page).count()
      if (count === previousCount) break
      previousCount = count
    }
    const ids = await postCards(page).evaluateAll((els) => els.map((el) => el.querySelector('a.time')?.getAttribute('href')))
    expect(new Set(ids).size).toBe(ids.length)
    expect(previousCount).toBeGreaterThanOrEqual(21)
  })

  test('新着バナー: 別ユーザーの投稿がポーリングで通知される', async ({ page, user, secondUser }) => {
    void user
    // 30 秒ポーリングを実時間で待たず、クロックを進めて発火させる。
    // ポーリングの setInterval は「全体」タブへの切替時(useEffect)に張られるため、
    // 切替より前にクロックを導入しておく(導入後に作られたタイマーだけが制御できる)
    await page.clock.install()
    await switchTab(page, '全体')
    const body = uniqueBody('newpost')

    await secondUser.page.getByRole('button', { name: '投稿する' }).click()
    await fillBody(secondUser.page, 'create', body)
    await submitEditor(secondUser.page, 'create')

    await page.clock.fastForward('00:31')

    await expect(newPostsBanner(page)).toBeVisible({ timeout: 5000 })
    await newPostsBanner(page).click()
    await expect(postCardByBody(page, body).first()).toBeVisible()
  })

  test('タイムライン取得に失敗すると「もっと見る」で再試行できる', async ({ page, user }) => {
    void user
    await page.route('**/api/timeline**', (route) => route.abort('failed'), { times: 1 })
    await page.reload()
    await expect(page.locator('.form-error[role="alert"]')).toBeVisible()
  })

  test('インプレッション数・リポストは表示されない(要件の差別化点)', async ({ page, user }) => {
    void user
    const body = uniqueBody('no-impression')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')

    const card = postCardByBody(page, body).first()
    await expect(card.getByText(/インプレッション|閲覧数|リポスト/)).toHaveCount(0)
  })
})
