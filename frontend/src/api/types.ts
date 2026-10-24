export interface User {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** アクセストークンの有効期間(秒) */
  expiresIn: number
  user: User
}

export interface RegisterInput {
  username: string
  displayName: string
  password: string
}

export interface LoginInput {
  username: string
  password: string
}

/** 投稿の画像。url は表示用の署名付き URL(期限つき)、sortOrder は 0〜3(表示順)。 */
export interface PostImage {
  url: string
  sortOrder: number
}

export interface Post {
  id: number
  body: string
  createdAt: string
  updatedAt: string
  author: User
  images: PostImage[]
  likeCount: number
  commentCount: number
  likedByMe: boolean
}

export interface Comment {
  id: number
  body: string
  createdAt: string
  author: User
}

export interface CommentPage {
  items: Comment[]
  /** 次ページが無いとき null */
  nextCursor: string | null
}

export interface TimelinePage {
  items: Post[]
  /** 次ページが無いとき null */
  nextCursor: string | null
}

export interface Profile {
  id: number
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  postCount: number
  followingCount: number
  followerCount: number
  followedByMe: boolean
  isMe: boolean
}

export interface FollowUser {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  bio: string | null
  /** 自分自身の行では null(フォローボタンを出さない) */
  followedByMe: boolean | null
}

export interface FollowUserPage {
  items: FollowUser[]
  /** 次ページが無いとき null */
  nextCursor: string | null
}

export type TimelineScope = 'following' | 'all'

/** 署名付き URL の発行結果。uploadUrl へ requiredHeaders を付けて PUT する。 */
export interface Presign {
  uploadUrl: string
  objectKey: string
  expiresIn: number
  requiredHeaders: Record<string, string>
}

export type UploadPurpose = 'POST' | 'AVATAR'

export interface UpdateProfileInput {
  displayName: string
  bio: string
  /** 省略=変更なし / null=アイコンを削除 / 文字列=アップロード済みの新しいアイコンのキー */
  avatarKey?: string | null
}
