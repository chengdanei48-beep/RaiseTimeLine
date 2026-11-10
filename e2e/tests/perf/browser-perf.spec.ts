import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { test, expect } from '../../fixtures/test.ts'
import { uniqueBody, makeUser, tinyPng } from '../../fixtures/data.ts'
import { VITALS_INIT_SCRIPT, readVitals, timeOperation } from '../../fixtures/vitals.ts'
import { openPostEditor, postCardByBody, postCards, switchTab, scrollToBottom } from '../../pages/home.ts'
import { fillBody, submitEditor } from '../../pages/post-editor.ts'
import { login, fillSignupForm, submitSignup } from '../../pages/auth.ts'
import { gotoPost } from '../../pages/post-detail.ts'
import { gotoSearch, search } from '../../pages/search.ts'

/**
 * 操作ごとの時間計測(ブラウザ性能)。@perf タグ付きで、既定では実行されない
 * (playwright.config.ts の grepInvert。E2E_INCLUDE_PERF=1 か `-Tag perf` で有効化)。
 * 大量データでの負荷は perf/browser/ が担当するため、ここは少量データでの「操作の応答性」を見る。
 */
const OUT_DIR = process.env.E2E_PERF_OUT_DIR ?? join(process.cwd(), '..', 'perf', 'results', `e2e-perf-${Date.now()}`)

// 通常条件の予算(ms)。perf/browser/measure.mjs の LIMITS と同じ考え方(要件「一覧1秒以内」が基準)
const BUDGETS = {
  loginToTimeline: 2500, // BCrypt 分を許容
  postSubmit: 1000,
  likeToggle: 500,
  tabSwitch: 1000,
  scrollLoadMore: 1500,
  postDetail: 1000,
  searchResult: 1000,
  lcp: 2500,
  cls: 0.1,
}

test('@perf 操作ごとの時間計測とWeb Vitals', async ({ page }) => {
  await page.addInitScript(VITALS_INIT_SCRIPT)
  const timings: { label: string; ms: number }[] = []
  const user = makeUser('perf')

  await timeOperation('signup_to_timeline', timings, async () => {
    await page.goto('/signup')
    await fillSignupForm(page, user)
    await submitSignup(page)
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  })

  await page.getByRole('button', { name: 'ログアウト' }).click()
  await timeOperation('login_to_timeline', timings, async () => {
    await login(page, user.username, user.password)
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  })
  expect(timings.at(-1)!.ms, 'login_to_timeline').toBeLessThan(BUDGETS.loginToTimeline)

  const body = uniqueBody('perf-post')
  await timeOperation('post_submit', timings, async () => {
    await openPostEditor(page)
    await fillBody(page, 'create', body)
    await submitEditor(page, 'create')
    await expect(postCardByBody(page, body).first()).toBeVisible()
  })
  expect(timings.at(-1)!.ms, 'post_submit').toBeLessThan(BUDGETS.postSubmit)

  const card = postCardByBody(page, body).first()
  await timeOperation('like_toggle', timings, async () => {
    await card.getByRole('button', { name: /いいね/ }).click()
    await expect(card.getByRole('button', { name: /いいね 1件/ })).toBeVisible()
  })
  expect(timings.at(-1)!.ms, 'like_toggle').toBeLessThan(BUDGETS.likeToggle)

  await timeOperation('tab_switch', timings, async () => {
    await switchTab(page, '全体')
    await expect(postCards(page).first()).toBeVisible()
  })
  expect(timings.at(-1)!.ms, 'tab_switch').toBeLessThan(BUDGETS.tabSwitch)

  await timeOperation('scroll_load_more', timings, async () => {
    const before = await postCards(page).count()
    await scrollToBottom(page)
    await page.waitForFunction((n) => document.querySelectorAll('article.post').length !== n, before, { timeout: 5000 }).catch(() => undefined)
  })

  const href = await card.locator('a.time').getAttribute('href')
  await timeOperation('post_detail', timings, async () => {
    await gotoPost(page, href!.split('/').pop()!)
    await expect(page.locator('article.post.detail')).toBeVisible()
  })
  expect(timings.at(-1)!.ms, 'post_detail').toBeLessThan(BUDGETS.postDetail)

  await timeOperation('search_result', timings, async () => {
    await gotoSearch(page)
    await search(page, user.username)
    await expect(page.locator('section[aria-label="検索結果"]')).toContainText(user.displayName)
  })
  expect(timings.at(-1)!.ms, 'search_result').toBeLessThan(BUDGETS.searchResult)

  const vitals = await readVitals(page)

  mkdirSync(OUT_DIR, { recursive: true })
  writeFileSync(
    join(OUT_DIR, 'operations.json'),
    JSON.stringify({ at: new Date().toISOString(), condition: 'normal', timings, vitals, budgets: BUDGETS }, null, 2),
  )
  console.log('操作ごとの時間(ms):', timings.map((t) => `${t.label}=${t.ms}`).join(', '))
  console.log(`LCP ${vitals.lcp.toFixed(0)}ms / CLS ${vitals.cls.toFixed(3)} / longTasks ${vitals.longTasks}`)

  expect(vitals.lcp, 'LCP').toBeLessThan(BUDGETS.lcp)
  expect(vitals.cls, 'CLS').toBeLessThan(BUDGETS.cls)
})

test('@perf CPU4x+低速回線での操作時間とメモリ', async ({ page, context, user }) => {
  void user
  const cdp = await context.newCDPSession(page)
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 })
  await cdp.send('Network.enable')
  await cdp.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: 150,
    downloadThroughput: (1.6 * 1024 * 1024) / 8,
    uploadThroughput: (750 * 1024) / 8,
  })
  await page.addInitScript(VITALS_INIT_SCRIPT)
  const timings: { label: string; ms: number }[] = []

  await timeOperation('reload_timeline_throttled', timings, async () => {
    await page.reload()
    await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  })

  // 画像つき投稿(実S3経由)をスロットル下で計測
  await timeOperation('image_post_throttled', timings, async () => {
    await openPostEditor(page)
    await fillBody(page, 'create', uniqueBody('perf-image'))
    await page.locator('#post-image-files').setInputFiles([{ name: 'a.png', mimeType: 'image/png', buffer: tinyPng() }])
    await submitEditor(page, 'create')
    await expect(page.locator('output.toast.show')).toContainText('投稿しました')
  })

  const vitals = await readVitals(page)
  const metrics = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((m) => [m.name, m.value]))

  mkdirSync(OUT_DIR, { recursive: true })
  writeFileSync(
    join(OUT_DIR, 'operations-throttled.json'),
    JSON.stringify(
      {
        at: new Date().toISOString(),
        condition: 'cpu4x+fast4g',
        timings,
        vitals,
        jsHeapUsedMB: +(metrics.JSHeapUsedSize / 1024 / 1024).toFixed(1),
        domNodes: metrics.Nodes,
      },
      null,
      2,
    ),
  )
  console.log('スロットル時の操作時間(ms):', timings.map((t) => `${t.label}=${t.ms}`).join(', '))
})
