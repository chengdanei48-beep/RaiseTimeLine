import { test, expect } from '../fixtures/test.ts'
import { uniqueBody } from '../fixtures/data.ts'
import { openPostEditor, postCardByBody, likeButton } from '../pages/home.ts'
import { fillBody, submitEditor } from '../pages/post-editor.ts'

test.describe('F-LIKE いいね', () => {
  test('いいねすると件数が+1され、取り消すと-1される', async ({ page, user }) => {
    void user
    const body = uniqueBody('like')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')

    const card = postCardByBody(page, body).first()
    const like = likeButton(card)
    await expect(like).toHaveAttribute('aria-label', /いいね 0件/)
    await like.click()
    await expect(like).toHaveAttribute('aria-pressed', 'true')
    await expect(like).toHaveAttribute('aria-label', /いいね 1件/)

    await like.click()
    await expect(like).toHaveAttribute('aria-pressed', 'false')
    await expect(like).toHaveAttribute('aria-label', /いいね 0件/)
  })

  test('API失敗時はロールバックしてエラーを表示する', async ({ page, user }) => {
    void user
    const body = uniqueBody('like-fail')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')

    const card = postCardByBody(page, body).first()
    const like = likeButton(card)

    await page.route('**/api/posts/*/like', (route) => route.fulfill({ status: 500, body: '{}' }))
    await like.click()

    await expect(like).toHaveAttribute('aria-pressed', 'false')
    await expect(card.locator('.form-error[role="alert"]')).toBeVisible()
  })

  test('別ユーザーのいいねが再取得後に反映される', async ({ page, user, secondUser }) => {
    void user
    const body = uniqueBody('like-other')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')
    const href = await postCardByBody(page, body).first().locator('a.time').getAttribute('href')

    await secondUser.page.goto(href ?? '/')
    await likeButton(secondUser.page.locator('article.post.detail')).click()

    await page.reload()
    const card = postCardByBody(page, body).first()
    await expect(likeButton(card)).toHaveAttribute('aria-label', /いいね 1件/)
  })
})
