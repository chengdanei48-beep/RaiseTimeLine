import { authRequest } from './client'
import type { Post, TimelinePage } from './types'

export function createPost(body: string): Promise<Post> {
  return authRequest<Post>('/api/posts', 'POST', { body })
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
