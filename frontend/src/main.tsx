import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './App'
import { AuthProvider } from './auth/AuthProvider'
import { ToastProvider } from './components/ToastProvider'
import './styles.css'

const root = document.getElementById('root')
if (!root) {
  throw new Error('#root が見つかりません')
}

createRoot(root).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <App />
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
