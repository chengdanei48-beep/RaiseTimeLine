import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // 既定ポートで起動できなければ失敗させる(別ポートに逃げない)
    port: 5173,
    strictPort: true,
    // バックエンドには CORS 設定が無いため、同一オリジンに見せて転送する(Cookie も同一オリジンで扱える)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // 本番ビルドの確認(性能テスト)用。開発サーバーと同じ 5173 で動かし、/api は server.proxy と同じ転送を使う
  preview: {
    port: 5173,
    strictPort: true,
  },
})
