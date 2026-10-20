import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { onSessionExpired } from '../api/client'
import type { LoginInput, RegisterInput, User } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<User | null>(null)

  const signIn = useCallback((u: User) => {
    setUser(u)
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(() => {
    setUser(null)
    setStatus('anonymous')
  }, [])

  // ページを開いたとき、リフレッシュトークン(Cookie)でログイン状態を復元する
  useEffect(() => {
    let cancelled = false
    authApi.restoreSession().then(
      (auth) => {
        if (!cancelled) signIn(auth.user)
      },
      () => {
        if (!cancelled) signOut()
      },
    )
    return () => {
      cancelled = true
    }
  }, [signIn, signOut])

  // API 呼び出しで認証が切れたら、ログアウト状態にする(RequireAuth がログイン画面へ戻す)
  useEffect(() => onSessionExpired(signOut), [signOut])

  const login = useCallback(
    async (input: LoginInput) => {
      signIn((await authApi.login(input)).user)
    },
    [signIn],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      signIn((await authApi.register(input)).user)
    },
    [signIn],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      signOut()
    }
  }, [signOut])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
