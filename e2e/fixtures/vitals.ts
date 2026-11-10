import type { Page } from '@playwright/test'

/** perf/browser/measure.mjs と同じ方式。LCP・CLS・ロングタスクをページ内で収集する */
export const VITALS_INIT_SCRIPT = `
  window.__vitals = { lcp: 0, cls: 0, longTasks: 0, longTaskMs: 0 };
  new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__vitals.lcp = e.startTime; })
    .observe({ type: 'largest-contentful-paint', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) window.__vitals.cls += e.value; })
    .observe({ type: 'layout-shift', buffered: true });
  new PerformanceObserver((l) => { for (const e of l.getEntries()) { window.__vitals.longTasks++; window.__vitals.longTaskMs += e.duration; } })
    .observe({ type: 'longtask', buffered: true });
`

export interface Vitals {
  lcp: number
  cls: number
  longTasks: number
  longTaskMs: number
}

export async function readVitals(page: Page): Promise<Vitals> {
  return page.evaluate(() => (window as unknown as { __vitals: Vitals }).__vitals)
}

/** 操作の所要時間を計測するヘルパー。結果を配列に積んでテスト終了時にまとめて出力・判定する */
export async function timeOperation<T>(label: string, results: { label: string; ms: number }[], fn: () => Promise<T>): Promise<T> {
  const started = Date.now()
  const result = await fn()
  results.push({ label, ms: Date.now() - started })
  return result
}
