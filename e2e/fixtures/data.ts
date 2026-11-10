/** テスト用データの生成。他テストと衝突しない一意な値を作る(並列実行・他テストの投稿混在への対策)。 */

/** ユーザーIDは 3〜15 文字の半角英数字と `_`。`e` + base36 乱数で短く一意にする。 */
export function uniqueUsername(prefix = 'e'): string {
  const rand = Math.random().toString(36).slice(2, 2 + 10)
  return `${prefix}${rand}`.slice(0, 15)
}

export const DEFAULT_PASSWORD = 'E2eTest#1234'

/** 検索・タイムラインの絞り込みに使う、他テストと混ざらない一意な本文。 */
export function uniqueBody(label = 'post'): string {
  return `e2e-${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export interface TestUser {
  username: string
  displayName: string
  password: string
  /** 内部ID。登録・ログイン後にフィクスチャが埋める(/users/:id の遷移に使う) */
  id?: number
}

export function makeUser(label = 'user'): TestUser {
  const username = uniqueUsername()
  return { username, displayName: `E2E ${label} ${username.slice(-4)}`, password: DEFAULT_PASSWORD }
}

/** 最小の有効な PNG(1x1)をメモリ上で生成する。フィクスチャファイルを置かずに画像アップロードを検証できる。 */
export function tinyPng(): Buffer {
  // 1x1 の透明 PNG(事前生成済みのバイト列)
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
}

/** 指定サイズ(バイト)の「画像らしい」ダミーファイル。サイズ上限の検証に使う(内容は読まれない想定)。 */
export function dummyFile(bytes: number): Buffer {
  return Buffer.alloc(bytes, 0)
}
