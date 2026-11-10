import type { Locator, Page } from '@playwright/test'

export function timelineSection(page: Page): Locator {
  return page.locator('section[aria-label="タイムライン"]')
}

export function postCards(page: Page): Locator {
  return timelineSection(page).locator('article.post')
}

/** 本文の一部で絞った、自分が作った投稿カード(他テストの投稿と混ざらないようにする) */
export function postCardByBody(page: Page, bodySubstring: string): Locator {
  return postCards(page).filter({ hasText: bodySubstring })
}

export async function switchTab(page: Page, tab: 'フォロー中' | '全体'): Promise<void> {
  await page.locator('nav[aria-label="タイムラインの種類"]').getByRole('button', { name: tab }).click()
}

export async function openPostEditor(page: Page): Promise<void> {
  await page.getByRole('button', { name: '投稿する' }).click()
}

export function newPostsBanner(page: Page): Locator {
  return page.locator('button.new-posts')
}

export async function scrollToBottom(page: Page): Promise<void> {
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
}

export function likeButton(card: Locator): Locator {
  return card.getByRole('button', { name: /いいね/ })
}

export function commentLink(card: Locator): Locator {
  return card.getByRole('link', { name: /コメント/ })
}
