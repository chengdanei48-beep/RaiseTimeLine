import { createContext, useContext } from 'react'

/** 画面下に短時間表示するお知らせ(完了メッセージ) */
export type ShowToast = (message: string) => void

export const ToastContext = createContext<ShowToast | null>(null)

export function useToast(): ShowToast {
  const value = useContext(ToastContext)
  if (!value) {
    throw new Error('useToast は ToastProvider の内側で使ってください')
  }
  return value
}
