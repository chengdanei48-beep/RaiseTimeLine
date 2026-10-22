import { useLocation, useNavigate } from 'react-router'

/** 「← 戻る」。来た画面へ戻り、直接開いた(履歴がない)ときはホームへ移る。 */
export function BackButton() {
  const navigate = useNavigate()
  const location = useLocation()

  function handleBack() {
    if (location.key === 'default') {
      navigate('/')
    } else {
      navigate(-1)
    }
  }

  return (
    <button className="btn-text back" type="button" onClick={handleBack}>
      ← 戻る
    </button>
  )
}
