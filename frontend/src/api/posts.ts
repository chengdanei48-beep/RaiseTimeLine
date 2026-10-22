import { authRequest } from './client'
import type { Post, TimelinePage, TimelineScope } from './types'

export function createPost(body: string): Promise<Post> {
  return authRequest<Post>('/api/posts', 'POST', { body })
}

export function updatePost(id: number, body: string): Promise<Post> {
  return authRequest<Post>(`/api/posts/${id}`, 'PATCH', { body })
}

export function deletePost(id: number): Promise<void> {
  return authRequest<void>(`/api/posts/${id}`, 'DELETE')
}

export function fetchPost(id: number): Promise<Post> {
  return authRequest<Post>(`/api/posts/${id}`, 'GET')
}

export function likePost(id: number): Promise<void> {
  return authRequest<void>(`/api/posts/${id}/like`, 'PUT')
}

export function unlikePost(id: number): Promise<void> {
  return authRequest<void>(`/api/posts/${id}/like`, 'DELETE')
}

export function fetchTimeline(scope: TimelineScope, cursor: string | null): Promise<TimelinePage> {
  const query = cursor === null ? '' : `&cursor=${encodeURIComponent(cursor)}`
  return authRequest<TimelinePage>(`/api/timeline?scope=${scope}${query}`, 'GET')
}
