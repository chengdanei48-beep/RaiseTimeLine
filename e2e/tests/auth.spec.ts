import { test, expect } from '@playwright/test'
import { makeUser } from '../fixtures/data.ts'
import { fillSignupForm, submitSignup, login, logout, expectHome } from '../pages/auth.ts'

test.describe('F-AUTH 認証', () => {
  test('登録するとそのままホームへ遷移する', async ({ page }) => {
    const user = makeUser('signup')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expectHome(page)
    await expect(page.getByRole('link', { name: '自分のプロフィール' })).toContainText(user.displayName)
  })

  test('大文字小文字違いでも重複登録はできない', async ({ page }) => {
    const user = makeUser('dup')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expectHome(page)

    await page.getByRole('button', { name: 'ログアウト' }).click()
    await page.goto('/signup')
    await fillSignupForm(page, { ...user, username: user.username.toUpperCase() })
    await submitSignup(page)
    await expect(page.locator('.error, .form-error').filter({ hasText: /既に使われています/ })).toBeVisible()
  })

  test('ユーザーIDは大文字小文字を区別せずログインできる', async ({ page }) => {
    const user = makeUser('case')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expectHome(page)
    await page.getByRole('button', { name: 'ログアウト' }).click()

    await login(page, user.username.toUpperCase(), user.password)
    await expectHome(page)
  })

  test('誤パスワードと存在しないIDで同じエラー文言が出る', async ({ page }) => {
    const user = makeUser('wrongpw')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await page.getByRole('button', { name: 'ログアウト' }).click()

    await login(page, user.username, 'WrongPassword#1')
    const wrongPwError = await page.locator('.form-error[role="alert"]').textContent()
    await page.goto('/login')
    await login(page, makeUser('nouser').username, 'WhateverPassword#1')
    await expect(page.locator('.form-error[role="alert"]')).toHaveText(wrongPwError ?? '')
  })

  test('パスワード確認が一致しないと登録できない', async ({ page }) => {
    const user = makeUser('mismatch')
    await page.goto('/signup')
    await fillSignupForm(page, user, `${user.password}x`)
    await submitSignup(page)
    await expect(page.locator('.error').filter({ hasText: '一致しません' })).toBeVisible()
    await expect(page).toHaveURL(/\/signup$/)
  })

  test.describe('検証エラー', () => {
    test('ユーザーIDの形式エラー', async ({ page }) => {
      const user = makeUser('fmt')
      await page.goto('/signup')
      await fillSignupForm(page, { ...user, username: 'a!' })
      await submitSignup(page)
      await expect(page.locator('.error').filter({ hasText: 'ユーザーIDは3〜15文字' })).toBeVisible()
    })

    test('パスワードが短すぎるとエラー', async ({ page }) => {
      const user = makeUser('short')
      await page.goto('/signup')
      await fillSignupForm(page, { ...user, password: 'short1' }, 'short1')
      await submitSignup(page)
      await expect(page.locator('.error').filter({ hasText: 'パスワードは8〜72文字' })).toBeVisible()
    })
  })

  test('リロードしてもログイン状態が維持される', async ({ page }) => {
    const user = makeUser('reload')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expectHome(page)

    await page.reload()
    await expectHome(page)
  })

  test('ログアウト後は保護された画面にアクセスするとログインへ戻る', async ({ page }) => {
    const user = makeUser('logout')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await logout(page)

    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await page.goto('/search')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('ログイン済みで /login を開くとホームへ戻る', async ({ page }) => {
    const user = makeUser('guestonly')
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expectHome(page) // 登録(ログイン・refresh Cookie 発行)の完了を待ってから遷移する
    await page.goto('/login')
    await expect(page).toHaveURL(/\/$/)
  })

  test('未認証では保護された画面からログインへリダイレクトする', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('存在しないURLはホームへリダイレクトされる(未認証ならログインへ)', async ({ page }) => {
    await page.goto('/no-such-page')
    await expect(page).toHaveURL(/\/login$/)
  })
})
