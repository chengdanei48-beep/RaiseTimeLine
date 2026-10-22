import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ToastContext } from './ToastContext'

/** トーストを表示してから消すまでの時間(ミリ秒) */
const TOAST_DURATION_MS = 2600

export function ToastProvider({ children }: { children: ReactNode }) {
  // 文言は消えるときも残す(フェードアウト中に空の枠が見えないように)
  const [message, setMessage] = useState('')
  const [visible, setVisible] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const show = useCallback((text: string) => {
    clearTimeout(timerRef.current)
    setMessage(text)
    setVisible(true)
    timerRef.current = setTimeout(() => setVisible(false), TOAST_DURATION_MS)
  }, [])

  useEffect(() => () => clearTimeout(timerRef.current), [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <output className={visible ? 'toast show' : 'toast'} aria-live="polite">
        {message}
      </output>
    </ToastContext.Provider>
  )
}
