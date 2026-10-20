import { useState, type SubmitEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Field } from '../components/Field'

/** S-02 ログイン */
export function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const errors = {
      username: username === '' ? 'ユーザーIDを入力してください' : undefined,
      password: password === '' ? 'パスワードを入力してください' : undefined,
    }
    setFieldErrors(errors)
    setFormError(null)
    if (errors.username || errors.password) return

    setSubmitting(true)
    try {
      // 成功するとログイン済みになり、GuestOnly が S-03 へ移動させる
      await login({ username, password })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setFormError(err.message)
      } else if (err instanceof ApiError && err.status === 400) {
        setFieldErrors({ username: err.errors.username, password: err.errors.password })
      } else {
        setFormError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      }
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-wrap card card-pad">
      <h1>RaiseTimeLine</h1>
      <p className="lead">ログイン</p>
      <form noValidate onSubmit={handleSubmit}>
        {formError && (
          <div className="form-error" role="alert">
            {formError}
          </div>
        )}
        <Field
          id="username"
          label="ユーザーID"
          value={username}
          onChange={setUsername}
          autoComplete="username"
          error={fieldErrors.username}
        />
        <Field
          id="password"
          label="パスワード"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          error={fieldErrors.password}
        />
        <button className="btn full" type="submit" disabled={submitting}>
          ログイン
        </button>
      </form>
      <div className="auth-links">
        はじめての方は <Link to="/signup">新規登録</Link>
      </div>
    </div>
  )
}
