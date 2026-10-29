// Lighthouse でログイン画面とログイン後のタイムラインを測る。スコアは参考値で、LCP / TBT / CLS の個別値で判定する。
// Playwright の Chromium をデバッグポート付きで起動し、ログイン済み(リフレッシュ Cookie)の状態で Lighthouse に渡す。
// 環境変数: BASE_URL, PERF_USERNAME, PERF_PASSWORD, OUT_DIR
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import lighthouse from 'lighthouse';
import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const USERNAME = process.env.PERF_USERNAME ?? 'perf_user_001';
const PASSWORD = process.env.PERF_PASSWORD ?? 'PerfTest#1234';
const OUT_DIR = process.env.OUT_DIR ?? 'results';
const PORT = 9222;

const LIMITS = { 'largest-contentful-paint': 2500, 'total-blocking-time': 300, 'cumulative-layout-shift': 0.1 };

const context = await chromium.launchPersistentContext(join(process.env.TMP_DIR ?? '.tmp', 'lh-profile'), {
  headless: true,
  args: [`--remote-debugging-port=${PORT}`],
});
const failures = [];
const report = {};

try {
  const page = context.pages()[0] ?? (await context.newPage());
  await page.goto(`${BASE_URL}/login`);
  await page.fill('#username', USERNAME);
  await page.fill('#password', PASSWORD);
  await page.click('button[type=submit]');
  await page.locator('article.post').first().waitFor();

  // ログイン後のタイムライン(リフレッシュ Cookie を残して再読み込みさせる)
  const run = await lighthouse(
    `${BASE_URL}/`,
    { port: PORT, output: 'json', onlyCategories: ['performance'] },
    { extends: 'lighthouse:default', settings: { disableStorageReset: true, formFactor: 'desktop', screenEmulation: { disabled: true } } },
  );
  report.timeline = { score: run.lhr.categories.performance.score };
  for (const [id, limit] of Object.entries(LIMITS)) {
    const v = run.lhr.audits[id].numericValue;
    report.timeline[id] = v;
    if (v > limit) failures.push(`timeline.${id}: ${v.toFixed(1)} > ${limit}`);
  }

  // ログイン画面は新しい(未ログインの)コンテキストで測る
  const guest = await chromium.launchPersistentContext(join(process.env.TMP_DIR ?? '.tmp', 'lh-guest'), {
    headless: true,
    args: ['--remote-debugging-port=9223'],
  });
  try {
    const run = await lighthouse(
      `${BASE_URL}/login`,
      { port: 9223, output: 'json', onlyCategories: ['performance'] },
      { extends: 'lighthouse:default', settings: { formFactor: 'desktop', screenEmulation: { disabled: true } } },
    );
    report.login = { score: run.lhr.categories.performance.score };
    for (const [id, limit] of Object.entries(LIMITS)) {
      const v = run.lhr.audits[id].numericValue;
      report.login[id] = v;
      if (v > limit) failures.push(`login.${id}: ${v.toFixed(1)} > ${limit}`);
    }
  } finally {
    await guest.close();
  }
} finally {
  await context.close();
}

mkdirSync(OUT_DIR, { recursive: true });
writeFileSync(join(OUT_DIR, 'lighthouse.json'), JSON.stringify({ report, failures }, null, 2));
console.log(JSON.stringify(report, null, 2));
if (failures.length > 0) {
  console.error('閾値違反:\n  ' + failures.join('\n  '));
  process.exit(1);
}
