import { createContext, useContext } from 'react'
import type { LoginInput, RegisterInput, User } from '../api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  /** loading: ページを開いた直後で、ログイン状態を復元している途中 */
  status: AuthStatus
  user: User | null
  login: (input: LoginInput) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  /** プロフィールを更新した後、ヘッダーなどの自分の情報を最新にする */
  updateUser: (user: User) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth は AuthProvider の内側で使ってください')
  }
  return value
}
