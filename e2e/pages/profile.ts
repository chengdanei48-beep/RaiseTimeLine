import type { Page } from '@playwright/test'

export function profileSection(page: Page) {
  return page.locator('section[aria-label="プロフィール"]')
}

export function followButton(page: Page) {
  return page.getByRole('button', { name: /フォロー(する|中)/ })
}

export async function gotoUser(page: Page, userId: number | string): Promise<void> {
  await page.goto(`/users/${userId}`)
}

export function followingCount(page: Page) {
  return profileSection(page).getByRole('link', { name: /フォロー中$/ })
}

export function followersCount(page: Page) {
  return profileSection(page).getByRole('link', { name: /フォロワー$/ })
}
