import { test, expect } from '../fixtures/test.ts'
import { uniqueBody } from '../fixtures/data.ts'
import { gotoUser, followButton, followingCount, followersCount } from '../pages/profile.ts'
import { postCardByBody, openPostEditor, switchTab } from '../pages/home.ts'
import { fillBody, submitEditor } from '../pages/post-editor.ts'

test.describe('F-FOLLOW フォロー', () => {
  test('フォローするとフォロワー数が+1され、フォロー中タイムラインに過去投稿が出る。解除すると消える', async ({
    page,
    user,
    secondUser,
  }) => {
    void user
    const body = uniqueBody('follow-post')
    await openPostEditor(secondUser.page)
    await fillBody(secondUser.page, 'create', body)
    await submitEditor(secondUser.page, 'create')

    await gotoUser(page, secondUser.user.id!)
    await expect(followersCount(page)).toContainText('0 フォロワー')
    await followButton(page).click()
    await expect(followButton(page)).toHaveText(/フォロー中/)
    await expect(followersCount(page)).toContainText('1 フォロワー')

    await page.goto('/')
    await switchTab(page, 'フォロー中')
    await expect(postCardByBody(page, body).first()).toBeVisible()

    await gotoUser(page, secondUser.user.id!)
    await followButton(page).click()
    await expect(followButton(page)).toHaveText('フォローする')

    await page.goto('/')
    await switchTab(page, 'フォロー中')
    await expect(postCardByBody(page, body)).toHaveCount(0)
  })

  test('自分のプロフィールにはフォローボタンが出ない', async ({ page, user }) => {
    await gotoUser(page, user.id!)
    await expect(page.getByRole('button', { name: /フォロー(する|中)/ })).toHaveCount(0)
    await expect(page.getByRole('link', { name: '編集' })).toBeVisible()
  })

  test('フォロー中・フォロワー一覧に反映される', async ({ page, user, secondUser }) => {
    await gotoUser(page, secondUser.user.id!)
    await followButton(page).click()
    await expect(followButton(page)).toHaveText(/フォロー中/)

    // 「フォロー中」一覧は自分(user)のもの。secondUser のプロフィールのカウントからではなく、
    // 自分のプロフィールから開く
    await gotoUser(page, user.id!)
    await followingCount(page).click()
    await expect(page.locator('section[aria-label="フォロー中"]')).toContainText(secondUser.user.displayName)
  })
})
