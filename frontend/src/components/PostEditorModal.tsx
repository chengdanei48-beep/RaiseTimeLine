import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, SubmitEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../api/client'
import type { User } from '../api/types'
import {
  AVATAR_TYPES,
  POST_IMAGE_MAX_BYTES,
  POST_IMAGE_MAX_COUNT,
  POST_MAX_LENGTH,
  validatePostBody,
  validatePostImageFile,
} from '../validation'
import { Avatar } from './Avatar'

/** 添付した画像(保存前)。previewUrl は選んだファイルのプレビュー用 URL。 */
interface AttachedImage {
  id: string
  file: File
  previewUrl: string
}

interface PostEditorModalProps {
  user: User
  title: string
  submitLabel: string
  /** 編集時の元の本文。作成時は空 */
  initialBody?: string
  /** 画像の添付を許可する(作成のみ。編集では画像を変更できない) */
  allowImages?: boolean
  /** 送信処理。files は添付した画像(選んだ順)。失敗は throw する(モーダルを開いたままエラーを表示する) */
  onSubmit: (body: string, files: File[]) => Promise<void>
  onClose: () => void
}

/** 投稿の作成・編集で共通のモーダル。表示中だけマウントして使う(入力は開くたびに初期化される)。 */
export function PostEditorModal({
  user,
  title,
  submitLabel,
  initialBody = '',
  allowImages = false,
  onSubmit,
  onClose,
}: PostEditorModalProps) {
  const [body, setBody] = useState(initialBody)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [images, setImages] = useState<AttachedImage[]>([])
  const [imageErrors, setImageErrors] = useState<string[]>([])

  // プレビュー用の URL は、取り消し・画面を閉じるときに解放する
  const imagesRef = useRef<AttachedImage[]>([])
  useEffect(
    () => () => {
      imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
    },
    [],
  )

  function handleFilesChange(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = '' // 同じファイルをもう一度選んでも変更が通知されるようにする
    const errors: string[] = []
    const added: AttachedImage[] = []
    for (const file of files) {
      const message = validatePostImageFile(file)
      if (message !== null) {
        errors.push(message)
      } else if (images.length + added.length >= POST_IMAGE_MAX_COUNT) {
        errors.push(`画像は${POST_IMAGE_MAX_COUNT}枚までです(${file.name} は追加できません)`)
      } else {
        added.push({ id: crypto.randomUUID(), file, previewUrl: URL.createObjectURL(file) })
      }
    }
    setImageErrors(errors)
    if (added.length > 0) {
      const next = [...images, ...added]
      imagesRef.current = next
      setImages(next)
    }
  }

  function removeImage(id: string) {
    const target = images.find((image) => image.id === id)
    if (target !== undefined) {
      URL.revokeObjectURL(target.previewUrl)
    }
    const next = images.filter((image) => image.id !== id)
    imagesRef.current = next
    setImages(next)
    setImageErrors([])
  }

  const dialogRef = useRef<HTMLDialogElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // ネイティブ <dialog>(フォーカストラップ・背景の操作不可)。開いたら本文の末尾にフォーカスする
  useEffect(() => {
    dialogRef.current?.showModal()
    const textarea = textareaRef.current
    if (textarea !== null) {
      textarea.focus()
      textarea.setSelectionRange(textarea.value.length, textarea.value.length)
    }
  }, [])

  const remaining = POST_MAX_LENGTH - body.trim().length
  const invalid = validatePostBody(body) !== null
  const dirty = body !== initialBody || images.length > 0

  /** 入力が残っているときは、破棄してよいか確認してから閉じる */
  function requestClose() {
    if (submitting) {
      return
    }
    if (dirty && !window.confirm('入力した内容を破棄しますか?')) {
      return
    }
    onClose()
  }

  // dialog 自身(余白を持たない)へのクリックは、背景(::backdrop)へのクリック。
  // Esc は keydown で処理する(cancel イベントは Chrome では中止できない場合があり、
  // 確認で「キャンセル」を選んでも閉じてしまうため)。IME の変換確定の Esc は無視する
  const requestCloseRef = useRef(requestClose)
  useEffect(() => {
    requestCloseRef.current = requestClose
  })
  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog === null) {
      return
    }
    const onClick = (e: Event) => {
      if (e.target === dialog) {
        requestCloseRef.current()
      }
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !e.isComposing) {
        e.preventDefault()
        requestCloseRef.current()
      }
    }
    dialog.addEventListener('click', onClick)
    dialog.addEventListener('keydown', onKeyDown)
    return () => {
      dialog.removeEventListener('click', onClick)
      dialog.removeEventListener('keydown', onKeyDown)
    }
  }, [])

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const message = validatePostBody(body)
    if (message !== null) {
      setError(message)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit(
        body.trim(),
        images.map((image) => image.file),
      )
      // 成功時は呼び出し側がモーダルを閉じる(アンマウントされる)
    } catch (err) {
      setError(err instanceof ApiError ? (err.errors.body ?? err.message) : GENERIC_ERROR)
      setSubmitting(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="modal"
      aria-labelledby="post-editor-title"
      onCancel={(e) => {
        // keydown 以外の閉じ要求(Android の戻る等)用。確認を挟むため既定の閉じ方を止める
        e.preventDefault()
        requestClose()
      }}
    >
      <form className="modal-body" onSubmit={handleSubmit} noValidate>
        <div className="modal-head">
          <h2 id="post-editor-title">{title}</h2>
          <span className="grow" />
          <button className="btn-text" type="button" aria-label="閉じる" onClick={requestClose}>
            ✕
          </button>
        </div>
        <div className="compose-row">
          <Avatar user={user} />
          <textarea
            ref={textareaRef}
            aria-label="本文"
            aria-invalid={error !== null ? true : undefined}
            aria-describedby={error !== null ? 'post-editor-error' : undefined}
            placeholder="いまどうしてる?"
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
        </div>
        {allowImages && images.length > 0 && (
          <ul className="attach-previews" aria-label="添付する画像">
            {images.map((image, index) => (
              <li key={image.id} className="attach-preview">
                <img src={image.previewUrl} alt={`添付画像 ${index + 1}`} />
                <button
                  className="attach-remove"
                  type="button"
                  aria-label={`添付画像 ${index + 1} を取り消す`}
                  onClick={() => removeImage(image.id)}
                  disabled={submitting}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
        )}
        {imageErrors.length > 0 && (
          <ul className="form-error attach-errors" role="alert">
            {imageErrors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        )}
        {error !== null && (
          <p id="post-editor-error" className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="compose-foot">
          {allowImages && (
            <>
              <label
                className={
                  images.length >= POST_IMAGE_MAX_COUNT || submitting
                    ? 'btn-text attach-button disabled'
                    : 'btn-text attach-button'
                }
                htmlFor="post-image-files"
              >
                🖼 画像を追加
              </label>
              <input
                id="post-image-files"
                className="visually-hidden"
                type="file"
                multiple
                accept={AVATAR_TYPES.join(',')}
                aria-describedby="post-image-hint"
                disabled={images.length >= POST_IMAGE_MAX_COUNT || submitting}
                onChange={handleFilesChange}
              />
              <span className="counter" id="post-image-hint">
                {images.length}/{POST_IMAGE_MAX_COUNT}枚・{POST_IMAGE_MAX_BYTES / 1024 / 1024}MB以下
              </span>
            </>
          )}
          <span className="grow" />
          <span className={remaining < 0 ? 'counter over' : 'counter'}>残り {remaining}</span>
          <button className="btn btn-outline" type="button" onClick={requestClose} disabled={submitting}>
            キャンセル
          </button>
          <button className="btn" type="submit" disabled={submitting || invalid || !dirty}>
            {submitLabel}
          </button>
        </div>
      </form>
    </dialog>
  )
}
