import { test, expect } from '../fixtures/test.ts'
import { gotoSearch, search, resultsSection } from '../pages/search.ts'

test.describe('F-SEARCH ユーザー検索', () => {
  test('ユーザーIDの一部で見つかる(大文字小文字を区別しない)', async ({ page, user, secondUser }) => {
    void user
    await gotoSearch(page)
    await search(page, secondUser.user.username.toUpperCase().slice(0, 8))
    await expect(resultsSection(page)).toContainText(secondUser.user.displayName)
  })

  test('先頭の @ を無視して同じ結果になる', async ({ page, user, secondUser }) => {
    void user
    await gotoSearch(page)
    await search(page, secondUser.user.username)
    const withoutAt = await resultsSection(page).textContent()

    await search(page, `@${secondUser.user.username}`)
    await expect(resultsSection(page)).toHaveText(withoutAt ?? '')
  })

  test('自分自身は検索結果に出ない', async ({ page, user }) => {
    await gotoSearch(page)
    await search(page, user.username)
    await expect(resultsSection(page)).not.toContainText(user.displayName)
  })

  test('未入力では検索せず案内文が出る。0件では該当なしの文言が出る', async ({ page, user }) => {
    void user
    await gotoSearch(page)
    await expect(page.getByText('ユーザーIDまたは表示名を入力してください')).toBeVisible()

    await search(page, 'no-such-user-xyz-zzz')
    await expect(page.getByText('該当するユーザーが見つかりません')).toBeVisible()
  })

  test('51文字以上はエラーになる', async ({ page, user }) => {
    void user
    // 検索語は送信(検索ボタン)で URL(?q=)に反映されてから検証されるため、入力後に送信する
    await gotoSearch(page)
    await search(page, 'a'.repeat(51))
    await expect(page.locator('#search-error')).toBeVisible()
  })

  test('検索語はURLの?qに保持される', async ({ page, user, secondUser }) => {
    void user
    await gotoSearch(page)
    await search(page, secondUser.user.username)
    await expect(page).toHaveURL(new RegExp(`\\?q=${secondUser.user.username}`))

    await page.goBack()
    await page.goForward()
    await expect(page.getByLabel('ユーザーIDまたは表示名')).toHaveValue(secondUser.user.username)
  })
})
