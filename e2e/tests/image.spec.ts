import { test, expect } from '../fixtures/test.ts'
import { uniqueBody, tinyPng, dummyFile } from '../fixtures/data.ts'
import { openPostEditor, postCardByBody } from '../pages/home.ts'
import { editorDialog, fillBody, submitEditor, attachImages } from '../pages/post-editor.ts'
import { gotoSettings, setAvatar, saveSettings, removeAvatar } from '../pages/settings.ts'

/**
 * 実 S3Mock(localhost:9000)を経由する。署名付き URL のホスト・CORS・画像表示まで通しで確認する
 * (scripts/e2e.ps1 が専用バケットを用意する想定。ローカル実行では docker-compose の既定バケットを使う)。
 */
test.describe('F-POST / F-PROF 画像(実 S3Mock)', () => {
  test('画像つき投稿が表示され、naturalWidth が取得できる(S3 CORS の実確認)', async ({ page, user }) => {
    void user
    const body = uniqueBody('image')
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await attachImages(page, 'create', [{ name: 'a.png', mimeType: 'image/png', buffer: tinyPng() }])
    await expect(editorDialog(page, 'create').locator('img[alt="添付画像 1"]')).toBeVisible()
    await submitEditor(page, 'create')

    const img = postCardByBody(page, body).first().locator('img[alt="投稿の画像 1"]')
    await expect(img).toBeVisible()
    await expect(img).toHaveJSProperty('complete', true)
    const width = await img.evaluate((el: HTMLImageElement) => el.naturalWidth)
    expect(width).toBeGreaterThan(0)
  })

  test('4枚添付でき、5枚目は拒否される', async ({ page, user }) => {
    void user
    await openPostEditor(page)
    await fillBody(page, 'create', uniqueBody('image4'))
    const files = Array.from({ length: 5 }, (_, i) => ({ name: `${i}.png`, mimeType: 'image/png', buffer: tinyPng() }))
    await attachImages(page, 'create', files)
    await expect(editorDialog(page, 'create').locator('ul[aria-label="添付する画像"] li')).toHaveCount(4)
    await expect(editorDialog(page, 'create').locator('.attach-errors')).toContainText('4枚まで')
  })

  test('6MBの画像とPDFは拒否される', async ({ page, user }) => {
    void user
    await openPostEditor(page)
    await attachImages(page, 'create', [
      { name: 'big.png', mimeType: 'image/png', buffer: dummyFile(6 * 1024 * 1024) },
    ])
    await expect(editorDialog(page, 'create').locator('.attach-errors')).toContainText('5MB 以下')

    await attachImages(page, 'create', [{ name: 'doc.pdf', mimeType: 'application/pdf', buffer: dummyFile(100) }])
    await expect(editorDialog(page, 'create').locator('.attach-errors')).toContainText('JPEG')
  })

  test('添付画像を取り消せる', async ({ page, user }) => {
    void user
    await openPostEditor(page)
    await attachImages(page, 'create', [{ name: 'a.png', mimeType: 'image/png', buffer: tinyPng() }])
    await editorDialog(page, 'create').getByRole('button', { name: /添付画像 1 を取り消す/ }).click()
    await expect(editorDialog(page, 'create').locator('ul[aria-label="添付する画像"] li')).toHaveCount(0)
  })

  test('アイコンを設定・削除できる', async ({ page, user }) => {
    await gotoSettings(page)
    await setAvatar(page, { name: 'avatar.png', mimeType: 'image/png', buffer: tinyPng() })
    await saveSettings(page)
    await expect(page.locator('output.toast.show')).toContainText('プロフィールを更新しました')

    await page.goto(`/users/${user.id}`)
    await expect(page.locator('section[aria-label="プロフィール"] img')).toBeVisible()

    await gotoSettings(page)
    await removeAvatar(page)
    await saveSettings(page)
    await page.goto(`/users/${user.id}`)
    await expect(page.locator('section[aria-label="プロフィール"] img')).toHaveCount(0)
  })
})

/** presign・PUT をスタブ化し、実 S3 を使わず障害系(429・PUT失敗)の画面挙動だけを速く確認する */
test.describe('F-POST 画像(スタブ)', () => {
  test('presignが429だとアップロードが失敗し、投稿されない', async ({ page, user }) => {
    void user
    await page.route('**/api/uploads/presign', (route) =>
      route.fulfill({ status: 429, headers: { 'Retry-After': '30' }, body: '{"detail":"操作が多すぎます。しばらくしてから再度お試しください"}' }),
    )
    await openPostEditor(page)
    await fillBody(page, 'create', uniqueBody('presign429'))
    await attachImages(page, 'create', [{ name: 'a.png', mimeType: 'image/png', buffer: tinyPng() }])
    await submitEditor(page, 'create')
    await expect(editorDialog(page, 'create').locator('.form-error, [role="alert"]')).toContainText('しばらくして')
  })

  test('S3へのPUTが失敗すると投稿されずエラーが出る', async ({ page, user }) => {
    void user
    await page.route('http://localhost:9000/**', (route) => route.fulfill({ status: 500, body: 'error' }))
    await openPostEditor(page)
    await fillBody(page, 'create', uniqueBody('put-fail'))
    await attachImages(page, 'create', [{ name: 'a.png', mimeType: 'image/png', buffer: tinyPng() }])
    await submitEditor(page, 'create')
    await expect(editorDialog(page, 'create')).toBeVisible()
    await expect(editorDialog(page, 'create').locator('.form-error, [role="alert"]')).toBeVisible()
  })
})
