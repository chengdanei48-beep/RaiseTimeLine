import { Outlet } from 'react-router'
import { AppHeader } from './AppHeader'

/** ログイン後の画面の枠。共通ヘッダーと、各画面の本体。 */
export function AppLayout() {
  return (
    <>
      <AppHeader />
      <main>
        <Outlet />
      </main>
    </>
  )
}
