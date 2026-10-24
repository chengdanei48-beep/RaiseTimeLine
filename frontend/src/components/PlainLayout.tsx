import { Outlet } from 'react-router'

/** ヘッダーのない画面(新規登録・ログイン)の枠。 */
export function PlainLayout() {
  return (
    <main>
      <Outlet />
    </main>
  )
}
