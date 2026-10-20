// バックエンド(RegisterRequest / LoginRequest)と同じ規則・メッセージ

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,15}$/
const PASSWORD_MAX_BYTES = 72

export function validateUsername(value: string): string | null {
  if (value === '') {
    return 'ユーザーIDを入力してください'
  }
  return USERNAME_PATTERN.test(value)
    ? null
    : 'ユーザーIDは3〜15文字の半角英数字と _ で入力してください'
}

/** 前後の空白を除いた値で検証する(サーバーも除いてから検証・保存する)。 */
export function validateDisplayName(value: string): string | null {
  const name = value.trim()
  if (name === '') {
    return '表示名を入力してください'
  }
  return name.length <= 30 ? null : '表示名は1〜30文字で入力してください'
}

export function validatePassword(value: string): string | null {
  if (value === '') {
    return 'パスワードを入力してください'
  }
  if (value.length < 8 || value.length > 72) {
    return 'パスワードは8〜72文字で入力してください'
  }
  // BCrypt は 72 バイトを超える部分を無視するため、UTF-8 のバイト数でも制限される
  if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
    return 'パスワードが長すぎます'
  }
  return null
}

export function validatePasswordConfirm(password: string, confirm: string): string | null {
  return password === confirm ? null : 'パスワードが一致しません'
}
