import { authRequest } from './client'
import type { FollowUserPage, Profile, TimelinePage } from './types'

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
