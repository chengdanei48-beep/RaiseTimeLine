import { test, expect } from '../fixtures/test.ts'
import { gotoSettings, saveSettings } from '../pages/settings.ts'
import { gotoUser } from '../pages/profile.ts'
import { gotoSearch, search, resultsSection } from '../pages/search.ts'

test.describe('F-PROF プロフィール', () => {
  test('表示名と自己紹介の更新が各所に反映される', async ({ page, user, secondUser }) => {
    const newName = `更新済み ${user.username.slice(-4)}`
    const newBio = `更新した自己紹介 ${user.username.slice(-4)}`

    await gotoSettings(page)
    await page.getByLabel('表示名').fill(newName)
    await page.getByLabel('自己紹介').fill(newBio)
    await saveSettings(page)

    await expect(page.locator('output.toast.show')).toContainText('プロフィールを更新しました')
    await expect(page).toHaveURL(new RegExp(`/users/${user.id}$`))
    await expect(page.locator('section[aria-label="プロフィール"]')).toContainText(newName)
    await expect(page.locator('section[aria-label="プロフィール"]')).toContainText(newBio)

    // 検索は自分自身を除外する仕様(F-SEARCH)なので、別ユーザーから検索する
    await gotoSearch(secondUser.page)
    await search(secondUser.page, newName)
    await expect(resultsSection(secondUser.page)).toContainText(newName)
  })

  test('ユーザーIDは変更できない(読み取り専用表示)', async ({ page, user }) => {
    await gotoSettings(page)
    await expect(page.locator('.readonly-value')).toContainText(`@${user.username}`)
    await expect(page.getByLabel('ユーザーID')).toHaveCount(0)
  })

  test('自己紹介が161文字だとエラーになる', async ({ page, user }) => {
    void user
    await gotoSettings(page)
    await page.getByLabel('自己紹介').fill('あ'.repeat(161))
    await saveSettings(page)
    await expect(page.locator('.form-error, .error').filter({ hasText: '160文字以内' })).toBeVisible()
  })

  test('存在しないユーザーのプロフィールは「ユーザーが見つかりません」', async ({ page, user }) => {
    void user
    await gotoUser(page, 999999999)
    await expect(page.locator('.empty')).toContainText('ユーザーが見つかりません')
  })
})
