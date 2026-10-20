import { useState, type SubmitEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Field } from '../components/Field'
import {
  validateDisplayName,
  validatePassword,
  validatePasswordConfirm,
  validateUsername,
} from '../validation'

interface FieldErrors {
  username?: string | null
  displayName?: string | null
  password?: string | null
  passwordConfirm?: string | null
}

/** S-01 新規登録 */
export function SignupPage() {
  const { register } = useAuth()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const found: FieldErrors = {
      username: validateUsername(username),
      displayName: validateDisplayName(displayName),
      password: validatePassword(password),
      passwordConfirm: validatePasswordConfirm(password, passwordConfirm),
    }
    setErrors(found)
    setFormError(null)
    if (Object.values(found).some(Boolean)) return

    setSubmitting(true)
    try {
      // 確認用パスワードは送らない。成功すると自動でログイン済みになり、GuestOnly が S-03 へ移動させる
      await register({ username, displayName: displayName.trim(), password })
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setErrors({ username: err.message })
      } else if (err instanceof ApiError && err.status === 400) {
        setErrors({
          username: err.errors.username,
          displayName: err.errors.displayName,
          password: err.errors.password,
        })
      } else {
        setFormError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      }
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-wrap card card-pad">
      <h1>RaiseTimeLine</h1>
      <p className="lead">アカウントを作成</p>
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
          autoComplete="off"
          hint="3〜15文字の半角英数字と _ (大文字小文字は区別されません)"
          error={errors.username}
        />
        <Field
          id="displayName"
          label="表示名"
          value={displayName}
          onChange={setDisplayName}
          autoComplete="off"
          hint="1〜30文字"
          error={errors.displayName}
        />
        <Field
          id="password"
          label="パスワード"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          hint="8〜72文字"
          error={errors.password}
        />
        <Field
          id="passwordConfirm"
          label="パスワード(確認)"
          type="password"
          value={passwordConfirm}
          onChange={setPasswordConfirm}
          autoComplete="new-password"
          error={errors.passwordConfirm}
        />
        <button className="btn full" type="submit" disabled={submitting}>
          登録する
        </button>
      </form>
      <div className="auth-links">
        アカウントをお持ちの方は <Link to="/login">ログイン</Link>
      </div>
    </div>
  )
}
