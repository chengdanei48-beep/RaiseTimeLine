import { authRequest } from './client'
import type { Post, TimelinePage } from './types'

/** scheduledAt(ISO 8601)を渡すと予約投稿になる。省略で即時投稿。 */
export function createPost(body: string, scheduledAt?: string): Promise<Post> {
  return authRequest<Post>('/api/posts', 'POST', scheduledAt === undefined ? { body } : { body, scheduledAt })
}

/** 自分の予約中の投稿(公開が近い順)。 */
export function fetchScheduled(): Promise<Post[]> {
  return authRequest<Post[]>('/api/posts/scheduled', 'GET')
}

export function updatePost(id: number, body: string): Promise<Post> {
  return authRequest<Post>(`/api/posts/${id}`, 'PATCH', { body })
}

export function deletePost(id: number): Promise<void> {
  return authRequest<void>(`/api/posts/${id}`, 'DELETE')
}

export function fetchTimeline(cursor: string | null): Promise<TimelinePage> {
  const query = cursor === null ? '' : `?cursor=${encodeURIComponent(cursor)}`
  return authRequest<TimelinePage>(`/api/timeline${query}`, 'GET')
}
