import { defineConfig, devices } from '@playwright/test'

/**
 * RaiseTimeLine の E2E テスト設定。Chromium のみ(方針決定済み)。
 * 対象は本番ビルド(vite preview, 5173)+ perf プロファイルの jar(8080)。
 * scripts/e2e.ps1 が専用 DB・専用 S3 バケットを用意してから実行する。
 */
export default defineConfig({
  testDir: './tests',
  // @perf タグの性能計測は実時間に依存するため、通常の機能テストとは別枠(手動 -Tag perf でのみ実行)
  grepInvert: process.env.E2E_INCLUDE_PERF === '1' ? undefined : /@perf/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // 並列度を上げすぎるとログイン時の BCrypt が CPU を奪い合って不安定になるため抑える
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['github']] : [['html', { open: 'never' }], ['list']],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ja-JP',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  outputDir: '../perf/results/e2e-artifacts',
})
