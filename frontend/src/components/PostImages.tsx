import type { PostImage } from '../api/types'

/** 投稿の画像。1枚=大きく、2枚=横2分割、3〜4枚=グリッド。 */
export function PostImages({ images }: { images: PostImage[] }) {
  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder)
  return (
    <div className={`images n${sorted.length}`}>
      {sorted.map((image, index) => (
        <img
          key={image.sortOrder}
          src={image.url}
          alt={`投稿の画像 ${index + 1}`}
          loading="lazy"
        />
      ))}
    </div>
  )
}
