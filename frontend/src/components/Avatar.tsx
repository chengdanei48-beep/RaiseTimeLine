import { useState } from 'react'
import type { User } from '../api/types'

const COLORS = ['#1d9bf0', '#7856ff', '#f91880', '#00ba7c', '#ff7a00', '#e0245e']

interface AvatarProps {
  user: Pick<User, 'id' | 'displayName'> & { avatarUrl?: string | null }
  size?: 'sm' | 'md' | 'lg'
  /** 保存前のプレビュー用。指定すると avatarUrl より優先する */
  previewUrl?: string | null
}

/**
 * アイコン。画像(avatarUrl)があれば表示し、無い・読み込めないときは表示名の頭文字を色付きの丸で表示する。
 * 署名付き URL には有効期限があるため、読み込みに失敗しても頭文字に戻るようにする。
 */
export function Avatar({ user, size = 'md', previewUrl = null }: AvatarProps) {
  const className = size === 'md' ? 'avatar' : `avatar ${size}`
  const src = previewUrl ?? user.avatarUrl ?? null
  // 読み込みに失敗した URL を覚える(URL が変わったら再び試す)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  if (src !== null && src !== failedSrc) {
    return <img className={className} src={src} alt="" onError={() => setFailedSrc(src)} />
  }
  return (
    <span
      className={className}
      style={{ background: COLORS[user.id % COLORS.length] }}
      aria-hidden="true"
    >
      {Array.from(user.displayName)[0] ?? '?'}
    </span>
  )
}
