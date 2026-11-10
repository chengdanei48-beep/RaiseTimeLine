import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'
import type { TestUser } from '../fixtures/data.ts'

export async function fillSignupForm(page: Page, user: TestUser, confirm = user.password): Promise<void> {
  await page.getByLabel('ユーザーID').fill(user.username)
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('パスワード', { exact: true }).fill(user.password)
  await page.getByLabel('パスワード(確認)').fill(confirm)
}

export async function submitSignup(page: Page): Promise<void> {
  await page.getByRole('button', { name: '登録する' }).click()
}

export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('ユーザーID').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

export async function expectHome(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
}
