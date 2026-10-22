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

export interface Post {
  id: number
  body: string
  createdAt: string
  updatedAt: string
  author: User
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
