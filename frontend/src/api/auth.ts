import { authRequest, publicRequest, refreshSession, setAccessToken } from './client'
import type { AuthResponse, LoginInput, RegisterInput, User } from './types'

export async function register(input: RegisterInput): Promise<AuthResponse> {
  const auth = await publicRequest<AuthResponse>('/api/auth/register', 'POST', input)
  setAccessToken(auth.accessToken)
  return auth
}

export async function login(input: LoginInput): Promise<AuthResponse> {
  const auth = await publicRequest<AuthResponse>('/api/auth/login', 'POST', input)
  setAccessToken(auth.accessToken)
  return auth
}

/** リフレッシュトークン(Cookie)でログイン状態を復元する。ページを開いたときに使う。 */
export function restoreSession(): Promise<AuthResponse> {
  return refreshSession()
}

/** サーバー側でリフレッシュトークンを失効させる。失敗してもブラウザ側のトークンは破棄する。 */
export async function logout(): Promise<void> {
  try {
    await publicRequest<void>('/api/auth/logout', 'POST')
  } finally {
    setAccessToken(null)
  }
}

export function me(): Promise<User> {
  return authRequest<User>('/api/auth/me', 'GET')
}
