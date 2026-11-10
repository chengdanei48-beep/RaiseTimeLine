import type { Page } from '@playwright/test'

/** 投稿作成・編集モーダル(ネイティブ dialog)。作成・編集で title が異なる。 */
export function editorDialog(page: Page, mode: 'create' | 'edit') {
  return page.getByRole('dialog', { name: mode === 'create' ? '投稿を作成' : '投稿を編集' })
}

export async function fillBody(page: Page, mode: 'create' | 'edit', body: string): Promise<void> {
  await editorDialog(page, mode).getByLabel('本文').fill(body)
}

export async function submitEditor(page: Page, mode: 'create' | 'edit'): Promise<void> {
  await editorDialog(page, mode)
    .getByRole('button', { name: mode === 'create' ? '投稿する' : '保存' })
    .click()
}

export async function closeEditor(page: Page, mode: 'create' | 'edit'): Promise<void> {
  await editorDialog(page, mode)
    .getByRole('button', { name: '閉じる' })
    .click()
}

export async function attachImages(page: Page, mode: 'create' | 'edit', files: { name: string; mimeType: string; buffer: Buffer }[]): Promise<void> {
  await editorDialog(page, mode).locator('#post-image-files').setInputFiles(files)
}
