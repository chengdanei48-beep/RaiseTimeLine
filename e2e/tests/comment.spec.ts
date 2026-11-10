import { test, expect } from '../fixtures/test.ts'
import { uniqueBody } from '../fixtures/data.ts'
import { openPostEditor, postCardByBody } from '../pages/home.ts'
import { fillBody, submitEditor } from '../pages/post-editor.ts'
import { gotoPost, detailArticle, commentsSection, postComment } from '../pages/post-detail.ts'

async function createPostAndOpenDetail(page: import('@playwright/test').Page, body: string) {
  await openPostEditor(page)
  await fillBody(page, 'create', body)
  await submitEditor(page, 'create')
  const href = await postCardByBody(page, body).first().locator('a.time').getAttribute('href')
  await page.goto(href ?? '/')
}

test.describe('F-CMT コメント', () => {
  test('コメントを投稿すると一覧に追加され件数が+1される', async ({ page, user }) => {
    void user
    const postBody = uniqueBody('cmt-post')
    await createPostAndOpenDetail(page, postBody)

    const commentBody = uniqueBody('cmt-body')
    await postComment(page, commentBody)

    await expect(commentsSection(page)).toContainText(commentBody)
    await expect(detailArticle(page).getByRole('link', { name: /コメント 1件/ })).toBeVisible()
  })

  test('他人のコメントには削除ボタンが出ない', async ({ page, user, secondUser }) => {
    void user
    const postBody = uniqueBody('cmt-other-post')
    await openPostEditor(page)
    await fillBody(page, 'create', postBody)
    await submitEditor(page, 'create')
    const href = await postCardByBody(page, postBody).first().locator('a.time').getAttribute('href')

    await secondUser.page.goto(href ?? '/')
    const commentBody = uniqueBody('cmt-by-other')
    await postComment(secondUser.page, commentBody)

    await page.goto(href ?? '/')
    const comment = commentsSection(page).getByText(commentBody).locator('..')
    await expect(comment.getByRole('button', { name: '削除' })).toHaveCount(0)
  })

  test('削除すると確認ダイアログを経て件数が-1される', async ({ page, user }) => {
    void user
    const postBody = uniqueBody('cmt-del-post')
    await createPostAndOpenDetail(page, postBody)
    const commentBody = uniqueBody('cmt-del-body')
    await postComment(page, commentBody)

    await commentsSection(page).getByText(commentBody).locator('..').getByRole('button', { name: '削除' }).click()
    await page.getByRole('dialog', { name: '削除の確認' }).getByRole('button', { name: '削除する' }).click()

    await expect(page.locator('output.toast.show')).toContainText('コメントを削除しました')
    await expect(detailArticle(page).getByRole('link', { name: /コメント 0件/ })).toBeVisible()
  })

  test('存在しない投稿IDでは「投稿が見つかりません」が出る', async ({ page, user }) => {
    void user
    await gotoPost(page, 999999999)
    await expect(page.locator('.empty')).toContainText('投稿が見つかりません')
  })
})
