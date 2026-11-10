import type { Page } from '@playwright/test'

export async function gotoSettings(page: Page): Promise<void> {
  await page.goto('/settings/profile')
}

export async function setAvatar(page: Page, file: { name: string; mimeType: string; buffer: Buffer }): Promise<void> {
  await page.locator('#avatar-file').setInputFiles(file)
}

export async function removeAvatar(page: Page): Promise<void> {
  await page.getByRole('button', { name: '画像を削除' }).click()
}

export async function saveSettings(page: Page): Promise<void> {
  await page.getByRole('button', { name: '保存' }).click()
}
