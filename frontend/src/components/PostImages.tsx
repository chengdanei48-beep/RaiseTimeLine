import type { PostImage } from '../api/types'

interface PostImagesProps {
  images: PostImage[]
  /** 画像の読み込みに失敗したとき(署名付き URL の期限切れなど)。呼び出し側が投稿を再取得して URL を更新する */
  onLoadError?: () => void
}

/** 投稿の画像。1枚=大きく、2枚=横2分割、3〜4枚=グリッド。 */
export function PostImages({ images, onLoadError }: PostImagesProps) {
  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder)
  return (
    <div className={`images n${sorted.length}`}>
      {sorted.map((image, index) => (
        <img
          key={image.sortOrder}
          src={image.url}
          alt={`投稿の画像 ${index + 1}`}
          loading="lazy"
          onError={onLoadError}
        />
      ))}
    </div>
  )
}
