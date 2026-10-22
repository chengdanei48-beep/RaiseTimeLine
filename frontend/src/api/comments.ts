import { authRequest } from './client'
import type { Comment, CommentPage } from './types'

export function fetchComments(postId: number, cursor: string | null): Promise<CommentPage> {
  const query = cursor === null ? '' : `?cursor=${encodeURIComponent(cursor)}`
  return authRequest<CommentPage>(`/api/posts/${postId}/comments${query}`, 'GET')
}

export function createComment(postId: number, body: string): Promise<Comment> {
  return authRequest<Comment>(`/api/posts/${postId}/comments`, 'POST', { body })
}

export function deleteComment(id: number): Promise<void> {
  return authRequest<void>(`/api/comments/${id}`, 'DELETE')
}
