import { authRequest } from './client'
import type { FollowUserPage, Profile, TimelinePage, UpdateProfileInput } from './types'

function cursorQuery(cursor: string | null): string {
  return cursor === null ? '' : `?cursor=${encodeURIComponent(cursor)}`
}

export function fetchProfile(id: number): Promise<Profile> {
  return authRequest<Profile>(`/api/users/${id}`, 'GET')
}

export function fetchUserPosts(id: number, cursor: string | null): Promise<TimelinePage> {
  return authRequest<TimelinePage>(`/api/users/${id}/posts${cursorQuery(cursor)}`, 'GET')
}

export function followUser(id: number): Promise<void> {
  return authRequest<void>(`/api/users/${id}/follow`, 'PUT')
}

export function unfollowUser(id: number): Promise<void> {
  return authRequest<void>(`/api/users/${id}/follow`, 'DELETE')
}

export function fetchFollowing(id: number, cursor: string | null): Promise<FollowUserPage> {
  return authRequest<FollowUserPage>(`/api/users/${id}/following${cursorQuery(cursor)}`, 'GET')
}

export function fetchFollowers(id: number, cursor: string | null): Promise<FollowUserPage> {
  return authRequest<FollowUserPage>(`/api/users/${id}/followers${cursorQuery(cursor)}`, 'GET')
}

export function searchUsers(q: string, cursor: string | null): Promise<FollowUserPage> {
  const cursorPart = cursor === null ? '' : `&cursor=${encodeURIComponent(cursor)}`
  return authRequest<FollowUserPage>(`/api/users?q=${encodeURIComponent(q)}${cursorPart}`, 'GET')
}

export function updateProfile(input: UpdateProfileInput): Promise<Profile> {
  return authRequest<Profile>('/api/users/me', 'PUT', input)
}
