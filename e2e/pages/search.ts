import type { Page } from '@playwright/test'

export async function gotoSearch(page: Page, q?: string): Promise<void> {
  await page.goto(q === undefined ? '/search' : `/search?q=${encodeURIComponent(q)}`)
}

export async function search(page: Page, keyword: string): Promise<void> {
  await page.getByLabel('ユーザーIDまたは表示名').fill(keyword)
  await page.getByRole('button', { name: '検索' }).click()
}

export function resultsSection(page: Page) {
  return page.locator('section[aria-label="検索結果"]')
}
