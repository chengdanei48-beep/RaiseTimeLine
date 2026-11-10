import type { Page } from '@playwright/test'

export async function gotoPost(page: Page, postId: number | string): Promise<void> {
  await page.goto(`/posts/${postId}`)
}

export function detailArticle(page: Page) {
  return page.locator('article.post.detail')
}

export function commentsSection(page: Page) {
  return page.locator('section[aria-label="コメント一覧"]')
}

export async function postComment(page: Page, body: string): Promise<void> {
  // 「コメント」の aria-label はリンク(コメント N件)等とも重なるため、textbox で絞る
  await page.getByRole('textbox', { name: 'コメント' }).fill(body)
  await page.getByRole('button', { name: '返信する' }).click()
}
