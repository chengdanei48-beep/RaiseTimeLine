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
})
