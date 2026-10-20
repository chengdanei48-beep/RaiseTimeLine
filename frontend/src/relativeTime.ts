/** 「2026/10/21 9:00」のような絶対表示(ローカル時刻)。 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ja-JP', { dateStyle: 'medium', timeStyle: 'short' })
}

/** 「たった今」「5分前」のような相対表示。 */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) {
    return 'たった今'
  }
  if (minutes < 60) {
    return `${minutes}分前`
  }
  if (minutes < 60 * 24) {
    return `${Math.floor(minutes / 60)}時間前`
  }
  return `${Math.floor(minutes / 60 / 24)}日前`
}
