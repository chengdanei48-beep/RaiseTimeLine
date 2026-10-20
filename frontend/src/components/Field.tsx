import type { ChangeEvent } from 'react'

interface FieldProps {
  id: string
  label: string
  type?: 'text' | 'password'
  value: string
  onChange: (value: string) => void
  autoComplete?: string
  hint?: string
  error?: string | null
}

/** ラベル・入力・ヒント・エラーをまとめた入力欄。 */
export function Field({
  id,
  label,
  type = 'text',
  value,
  onChange,
  autoComplete,
  hint,
  error,
}: FieldProps) {
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
      />
      {hint && (
        <div className="hint" id={hintId}>
          {hint}
        </div>
      )}
      {error && (
        <div className="error" id={errorId} role="alert">
          {error}
        </div>
      )}
    </div>
  )
}
