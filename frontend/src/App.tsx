import { Navigate, Route, Routes } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { AppLayout } from './components/AppLayout'
import { PlainLayout } from './components/PlainLayout'
import { FollowListPage } from './pages/FollowListPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfilePage } from './pages/ProfilePage'
import { SignupPage } from './pages/SignupPage'

export function App() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route element={<PlainLayout />}>
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Route>
      </Route>
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/posts/:id" element={<PostDetailPage />} />
          <Route path="/users/:id" element={<ProfilePage />} />
          <Route path="/users/:id/following" element={<FollowListPage kind="following" />} />
          <Route path="/users/:id/followers" element={<FollowListPage kind="followers" />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
