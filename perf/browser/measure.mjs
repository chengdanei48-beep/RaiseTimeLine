// ブラウザでの体感性能を測る(Playwright / Chromium)。本番ビルド(vite preview)に対して実行する。
// 環境変数: BASE_URL, PERF_USERNAME, PERF_PASSWORD, OUT_DIR, THROTTLE(1 で CPU 4x + 回線 Fast 4G 相当)
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const USERNAME = process.env.PERF_USERNAME ?? 'perf_user_001';
const PASSWORD = process.env.PERF_PASSWORD ?? 'PerfTest#1234';
const THROTTLE = process.env.THROTTLE === '1';
const OUT_DIR = process.env.OUT_DIR ?? 'results';
const SCROLL_TARGET = 100; // この件数まで無限スクロールで読み込む

// 合否基準(要件「一覧 1 秒以内」。スロットル時は緩める)
const LIMITS = THROTTLE
  ? { timelineVisibleMs: 3000, lcpMs: 4000, cls: 0.1, scrollLoadMs: 8000 }
  : { timelineVisibleMs: 1000, lcpMs: 2500, cls: 0.1, scrollLoadMs: 4000 };

const INIT_SCRIPT = `
  window.__perf = { lcp: 0, cls: 0, longTasks: 0, longTaskMs: 0, inp: 0 };
  new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__perf.lcp = e.startTime; })
    .observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) window.__perf.cls += e.value; })
    .observe({ type: 'layout-shift', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) { window.__perf.longTasks++; window.__perf.longTaskMs += e.duration; } })
    .observe({ type: 'longtask', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__perf.inp = Math.max(window.__perf.inp, e.duration); })
    .observe({ type: 'event', durationThreshold: 16, buffered: true });
`;

const steps = [];
const failures = [];

function check(name, value, limit) {
  const ok = value <= limit;
  if (!ok) failures.push(`${name}: ${value.toFixed(1)} > ${limit}`);
  return ok;
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ja-JP' });
  await context.addInitScript(INIT_SCRIPT);
  const page = await context.newPage();
  const cdp = await context.newCDPSession(page);
  await cdp.send('Performance.enable');

  if (THROTTLE) {
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 150,
      downloadThroughput: (1.6 * 1024 * 1024) / 8,
      uploadThroughput: (750 * 1024) / 8,
    });
  }

  let requests = 0;
  let bytes = 0;
  const apiTimes = [];
  page.on('response', async (res) => {
    requests++;
    const len = Number(res.headers()['content-length'] ?? 0);
    bytes += Number.isFinite(len) ? len : 0;
    if (new URL(res.url()).pathname.startsWith('/api/')) {
      const t = res.request().timing();
      if (t.responseEnd > 0) apiTimes.push({ path: new URL(res.url()).pathname, ms: t.responseEnd });
    }
  });

  async function vitals() {
    return page.evaluate(() => ({ ...window.__perf }));
  }

  async function step(name, action, readyLocator) {
    requests = 0;
    bytes = 0;
    const started = Date.now();
    await action();
    await page.locator(readyLocator).first().waitFor({ state: 'visible', timeout: 30000 });
    const visibleMs = Date.now() - started;
    steps.push({ name, visibleMs, requests, bytes, vitals: await vitals() });
    return visibleMs;
  }

  // 1. ログイン画面の初期表示
  await step('login_page', () => page.goto(`${BASE_URL}/login`, { waitUntil: 'load' }), '#username');

  // 2. ログイン → タイムライン(S-03)の初期表示。要件の「一覧 1 秒以内」はここ
  await page.fill('#username', USERNAME);
  await page.fill('#password', PASSWORD);
  const homeMs = await step(
    'login_to_timeline',
    () => page.click('button[type=submit]'),
    'section[aria-label="タイムライン"] article.post',
  );
  check('login_to_timeline.visibleMs', homeMs, LIMITS.timelineVisibleMs + 1500); // BCrypt 分を許容

  // 3. 無限スクロールで SCROLL_TARGET 件まで読み込み
  const t0 = Date.now();
  let count = await page.locator('article.post').count();
  while (count < SCROLL_TARGET) {
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    try {
      await page.waitForFunction(
        (before) => document.querySelectorAll('article.post').length > before,
        count,
        { timeout: 10000 },
      );
    } catch {
      break; // 件数が足りない(次ページ無し)
    }
    count = await page.locator('article.post').count();
  }
  const scrollMs = Date.now() - t0;
  const m = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((x) => [x.name, x.value]));
  steps.push({
    name: 'infinite_scroll',
    posts: count,
    visibleMs: scrollMs,
    jsHeapUsedMB: +(m.JSHeapUsedSize / 1024 / 1024).toFixed(1),
    domNodes: m.Nodes,
    vitals: await vitals(),
  });
  check('infinite_scroll.visibleMs', scrollMs, LIMITS.scrollLoadMs * Math.ceil(SCROLL_TARGET / 20));

  // 4. 「全体」タブ
  await page.evaluate(() => window.scrollTo(0, 0));
  await step(
    'switch_to_all_tab',
    () => page.click('nav.tabs button:has-text("全体")'),
    'section[aria-label="タイムライン"] article.post',
  );

  // 5. 投稿詳細
  await step('post_detail', () => page.locator('article.post a.time').first().click(), 'article.post.detail');

  // 6. プロフィール
  await step('profile', () => page.locator('article.post a.name').first().click(), 'section.profile-top');
  // 7. ユーザー検索
  await step('search', async () => {
    await page.goto(`${BASE_URL}/search`, { waitUntil: 'load' });
    await page.fill('input[type=search]', 'perf_user_0');
    await page.click('button[type=submit]');
  }, 'a[href^="/users/"]');

  const finalVitals = await vitals();
  check('lcp', finalVitals.lcp, LIMITS.lcpMs);
  check('cls', finalVitals.cls, LIMITS.cls);

  await browser.close();

  const result = {
    at: new Date().toISOString(),
    baseUrl: BASE_URL,
    throttle: THROTTLE,
    limits: LIMITS,
    steps,
    slowestApi: apiTimes.sort((a, b) => b.ms - a.ms).slice(0, 5),
    failures,
  };
  mkdirSync(OUT_DIR, { recursive: true });
  const file = join(OUT_DIR, THROTTLE ? 'browser-throttled.json' : 'browser.json');
  writeFileSync(file, JSON.stringify(result, null, 2));

  for (const s of steps) {
    console.log(`${s.name.padEnd(20)} ${String(s.visibleMs).padStart(6)} ms` + (s.posts ? ` (${s.posts}件, heap ${s.jsHeapUsedMB}MB, DOM ${s.domNodes})` : ''));
  }
  console.log(`LCP ${finalVitals.lcp.toFixed(0)} ms / CLS ${finalVitals.cls.toFixed(3)} / long tasks ${finalVitals.longTasks} (${finalVitals.longTaskMs.toFixed(0)} ms) / INP相当 ${finalVitals.inp} ms`);
  console.log(`結果: ${file}`);
  if (failures.length > 0) {
    console.error('閾値違反:\n  ' + failures.join('\n  '));
    process.exit(1);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
