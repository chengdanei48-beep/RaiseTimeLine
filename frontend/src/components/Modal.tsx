import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
}

/** ネイティブ <dialog> のモーダル(フォーカストラップ・Esc で閉じる)。背景クリックでも閉じる。 */
export function Modal({ title, onClose, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  // 親が毎回新しい関数を渡しても、showModal() を再実行しないよう ref 経由で呼ぶ
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const dialog = ref.current
    if (dialog === null) {
      return
    }
    dialog.showModal()
    // dialog 自体へのクリック = 背景(::backdrop)のクリック
    const closeOnBackdrop = (e: MouseEvent) => {
      if (e.target === dialog) {
        onCloseRef.current()
      }
    }
    dialog.addEventListener('click', closeOnBackdrop)
    return () => dialog.removeEventListener('click', closeOnBackdrop)
  }, [])

  return (
    <dialog ref={ref} className="modal" aria-labelledby={titleId} onCancel={onClose}>
      <div className="modal-body">
        <h2 id={titleId}>{title}</h2>
        {children}
      </div>
    </dialog>
  )
}
