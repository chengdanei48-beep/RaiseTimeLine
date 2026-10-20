import type { AuthResponse } from './types'

export const GENERIC_ERROR = 'しばらくしてから再度お試しください'

/** バックエンドの ProblemDetail(`detail`、項目別の `errors`)を包んだエラー。status 0 は通信失敗。 */
export class ApiError extends Error {
  readonly status: number
  readonly errors: Record<string, string>

  constructor(status: number, message: string, errors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errors = errors
  }
}

// アクセストークンはメモリにだけ置く(リロードすると消え、リフレッシュトークンの Cookie から復元する)
let accessToken: string | null = null
let sessionExpiredHandler: (() => void) | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** 認証が切れて再ログインが必要になったときに呼ばれる関数を登録する。解除用の関数を返す。 */
export function onSessionExpired(handler: () => void): () => void {
  sessionExpiredHandler = handler
  return () => {
    if (sessionExpiredHandler === handler) {
      sessionExpiredHandler = null
    }
  }
}

async function send(
  path: string,
  method: string,
  body: unknown,
  token: string | null,
): Promise<Response> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, GENERIC_ERROR)
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  let detail = GENERIC_ERROR
  let errors: Record<string, string> = {}
  try {
    const problem: unknown = await res.json()
    if (problem && typeof problem === 'object') {
      const p = problem as { detail?: unknown; errors?: unknown }
      if (typeof p.detail === 'string') {
        detail = p.detail
      }
      if (p.errors && typeof p.errors === 'object') {
        errors = p.errors as Record<string, string>
      }
    }
  } catch {
    // JSON でない応答(プロキシのエラーなど)は汎用メッセージにする
  }
  return new ApiError(res.status, detail, errors)
}

async function readBody<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw await toApiError(res)
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

/**
 * アクセストークンを付けずに呼ぶ API(登録・ログイン・更新・ログアウト)。
 * 401 でもトークン更新やセッション切れの処理はしない(呼び出し側が結果を扱う)。
 */
export async function publicRequest<T>(path: string, method: string, body?: unknown): Promise<T> {
  return readBody<T>(await send(path, method, body, null))
}

let refreshing: Promise<AuthResponse> | null = null

/**
 * リフレッシュトークン(Cookie)でアクセストークンを更新する。
 * リフレッシュトークンは使い捨てなので、同時に呼ばれても通信は 1 回にまとめる
 * (React の StrictMode による二重実行や、複数リクエストの同時 401 への対策)。
 */
export function refreshSession(): Promise<AuthResponse> {
  refreshing ??= (async () => {
    try {
      const auth = await publicRequest<AuthResponse>('/api/auth/refresh', 'POST')
      accessToken = auth.accessToken
      return auth
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

/**
 * ログインが必要な API。401 ならトークンを更新して 1 回だけやり直し、
 * それでも認証できなければセッション切れとして扱う。
 */
export async function authRequest<T>(path: string, method: string, body?: unknown): Promise<T> {
  let res = await send(path, method, body, accessToken)
  if (res.status === 401) {
    try {
      await refreshSession()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        expireSession()
      }
      throw e
    }
    res = await send(path, method, body, accessToken)
    if (res.status === 401) {
      expireSession()
    }
  }
  return readBody<T>(res)
}

function expireSession(): void {
  accessToken = null
  sessionExpiredHandler?.()
}
