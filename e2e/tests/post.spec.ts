import { test, expect } from '../fixtures/test.ts'
import { uniqueBody } from '../fixtures/data.ts'
import { openPostEditor, postCardByBody, switchTab } from '../pages/home.ts'
import { editorDialog, fillBody, submitEditor, closeEditor } from '../pages/post-editor.ts'

test.describe('F-POST 投稿', () => {
  test('テキスト投稿が先頭に追加され、トーストが出る', async ({ page, user }) => {
    void user
    const body = uniqueBody('create')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')

    await expect(page.locator('output.toast.show')).toContainText('投稿しました')
    await expect(postCardByBody(page, body).first()).toBeVisible()
  })

  test('本文が281文字だと投稿ボタンが無効になる', async ({ page, user }) => {
    void user
    await openPostEditor(page)
    await fillBody(page, 'create', 'あ'.repeat(281))
    await expect(editorDialog(page, 'create').getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  test('編集すると(編集済み)が表示される', async ({ page, user }) => {
    void user
    const original = uniqueBody('edit-before')
    const edited = uniqueBody('edit-after')
    await openPostEditor(page)
    await fillBody(page, 'create', original)
    await submitEditor(page, 'create')

    const card = postCardByBody(page, original).first()
    await card.getByRole('button', { name: '編集' }).click()
    await fillBody(page, 'edit', edited)
    await submitEditor(page, 'edit')

    const editedCard = postCardByBody(page, edited).first()
    await expect(editedCard.locator('.edited')).toBeVisible()
  })

  test('削除すると確認ダイアログを経て一覧から消える', async ({ page, user }) => {
    void user
    const body = uniqueBody('delete')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')

    const card = postCardByBody(page, body).first()
    await card.getByRole('button', { name: '削除' }).click()
    await page.getByRole('dialog', { name: '削除の確認' }).getByRole('button', { name: '削除する' }).click()

    await expect(page.locator('output.toast.show')).toContainText('投稿を削除しました')
    await expect(postCardByBody(page, body)).toHaveCount(0)
  })

  test('入力中に閉じようとすると破棄の確認が出る', async ({ page, user }) => {
    void user
    await openPostEditor(page)
    await fillBody(page, 'create', uniqueBody('discard'))

    page.once('dialog', (dialog) => dialog.dismiss())
    await closeEditor(page, 'create')
    await expect(editorDialog(page, 'create')).toBeVisible()

    page.once('dialog', (dialog) => dialog.accept())
    await closeEditor(page, 'create')
    await expect(editorDialog(page, 'create')).not.toBeVisible()
  })

  test('他人の投稿には編集・削除ボタンが出ない', async ({ page, user, secondUser }) => {
    void user
    const body = uniqueBody('other')
    await secondUser.page.getByRole('button', { name: '投稿する' }).click()
    await fillBody(secondUser.page, 'create', body)
    await submitEditor(secondUser.page, 'create')

    await switchTab(page, '全体')
    const card = postCardByBody(page, body).first()
    await expect(card).toBeVisible()
    await expect(card.getByRole('button', { name: '編集' })).toHaveCount(0)
    await expect(card.getByRole('button', { name: '削除' })).toHaveCount(0)
  })
})
