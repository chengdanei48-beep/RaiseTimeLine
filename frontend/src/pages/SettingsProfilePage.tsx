import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, SubmitEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../api/client'
import type { Profile } from '../api/types'
import { presignUpload, putToStorage } from '../api/uploads'
import { fetchProfile, updateProfile } from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { BackButton } from '../components/BackButton'
import { Field } from '../components/Field'
import { useToast } from '../components/ToastContext'
import {
  AVATAR_MAX_BYTES,
  AVATAR_TYPES,
  BIO_MAX_LENGTH,
  validateAvatarFile,
  validateBio,
  validateDisplayName,
} from '../validation'

type Status = 'loading' | 'ready' | 'error'

/** S-06 プロフィール編集。現在の内容を取得してからフォームを表示する。 */
export function SettingsProfilePage() {
  const { user } = useAuth()
  const [status, setStatus] = useState<Status>('loading')
  const [profile, setProfile] = useState<Profile | null>(null)
  const userId = user?.id

  useEffect(() => {
    let cancelled = false
    if (userId === undefined) {
      return
    }
    fetchProfile(userId)
      .then((loaded) => {
        if (!cancelled) {
          setProfile(loaded)
          setStatus('ready')
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('error')
        }
      })
    return () => {
      cancelled = true
    }
  }, [userId])

  return (
    <>
      <header className="topbar">
        <BackButton />
        <h1>プロフィールを編集</h1>
      </header>
      {status === 'loading' && <span className="loading">読み込み中…</span>}
      {status === 'error' && (
        <p className="form-error" role="alert">
          {GENERIC_ERROR}
        </p>
      )}
      {status === 'ready' && profile !== null && <ProfileForm profile={profile} />}
    </>
  )
}

/** アイコンの変更内容。keep=変更なし / remove=削除 / new=選んだ画像(保存時にアップロード) */
type AvatarChange =
  | { kind: 'keep' }
  | { kind: 'remove' }
  | { kind: 'new'; file: File; previewUrl: string }

function ProfileForm({ profile }: { profile: Profile }) {
  const { updateUser } = useAuth()
  const navigate = useNavigate()
  const showToast = useToast()

  const [displayName, setDisplayName] = useState(profile.displayName)
  const [bio, setBio] = useState(profile.bio ?? '')
  const [avatar, setAvatar] = useState<AvatarChange>({ kind: 'keep' })
  const [nameError, setNameError] = useState<string | null>(null)
  const [bioError, setBioError] = useState<string | null>(null)
  const [avatarError, setAvatarError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // 選んだ画像のプレビュー用 URL は、差し替え・画面を離れるときに解放する
  const previewUrlRef = useRef<string | null>(null)
  useEffect(
    () => () => {
      if (previewUrlRef.current !== null) {
        URL.revokeObjectURL(previewUrlRef.current)
      }
    },
    [],
  )

  function replaceAvatar(next: AvatarChange) {
    if (previewUrlRef.current !== null) {
      URL.revokeObjectURL(previewUrlRef.current)
    }
    previewUrlRef.current = next.kind === 'new' ? next.previewUrl : null
    setAvatar(next)
  }

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = '' // 同じファイルをもう一度選んでも変更が通知されるようにする
    if (file === undefined) {
      return
    }
    const message = validateAvatarFile(file)
    if (message !== null) {
      setAvatarError(message)
      return
    }
    setAvatarError(null)
    replaceAvatar({ kind: 'new', file, previewUrl: URL.createObjectURL(file) })
  }

  async function handleSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    const foundName = validateDisplayName(displayName)
    const foundBio = validateBio(bio)
    setNameError(foundName)
    setBioError(foundBio)
    setFormError(null)
    if (foundName !== null || foundBio !== null) {
      return
    }

    setSubmitting(true)
    try {
      // 新しい画像は、署名付き URL で S3 へ直接アップロードしてから、キーを保存する
      let avatarKey: string | null | undefined
      if (avatar.kind === 'new') {
        const presign = await presignUpload('AVATAR', avatar.file.type, avatar.file.size)
        await putToStorage(presign, avatar.file)
        avatarKey = presign.objectKey
      } else if (avatar.kind === 'remove') {
        avatarKey = null
      }
      const updated = await updateProfile({
        displayName: displayName.trim(),
        bio: bio.trim(),
        avatarKey,
      })
      updateUser({
        id: updated.id,
        username: updated.username,
        displayName: updated.displayName,
        avatarUrl: updated.avatarUrl,
      })
      showToast('プロフィールを更新しました')
      void navigate(`/users/${updated.id}`)
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && Object.keys(err.errors).length > 0) {
        setNameError(err.errors.displayName ?? null)
        setBioError(err.errors.bio ?? null)
      } else {
        setFormError(err instanceof ApiError ? err.message : GENERIC_ERROR)
      }
      setSubmitting(false)
    }
  }

  const shownUser = {
    id: profile.id,
    displayName: displayName.trim() === '' ? profile.displayName : displayName,
    avatarUrl: avatar.kind === 'remove' ? null : profile.avatarUrl,
  }
  const hasAvatar =
    avatar.kind === 'new' || (avatar.kind === 'keep' && profile.avatarUrl !== null)
  const bioRemaining = BIO_MAX_LENGTH - bio.trim().length

  return (
    <form className="card settings-form" onSubmit={handleSubmit} noValidate>
      <div className="avatar-edit">
        <Avatar
          user={shownUser}
          size="lg"
          previewUrl={avatar.kind === 'new' ? avatar.previewUrl : null}
        />
        <div className="avatar-edit-actions">
          <div className="row">
            <label className="btn btn-outline" htmlFor="avatar-file">
              画像を選択
            </label>
            <input
              id="avatar-file"
              className="visually-hidden"
              type="file"
              accept={AVATAR_TYPES.join(',')}
              aria-describedby="avatar-hint"
              onChange={handleFileChange}
            />
            {hasAvatar && (
              <button
                className="btn btn-outline"
                type="button"
                onClick={() => replaceAvatar({ kind: 'remove' })}
              >
                画像を削除
              </button>
            )}
          </div>
          <span className="hint muted" id="avatar-hint">
            JPEG・PNG・GIF・WebP、{AVATAR_MAX_BYTES / 1024 / 1024}MB以下
          </span>
          {avatarError !== null && (
            <span className="form-error" role="alert">
              {avatarError}
            </span>
          )}
        </div>
      </div>

      <div className="field">
        <span className="label-text">ユーザーID</span>
        <div className="readonly-value">@{profile.username}(変更できません)</div>
      </div>

      <Field
        id="displayName"
        label="表示名"
        value={displayName}
        onChange={setDisplayName}
        autoComplete="nickname"
        hint="1〜30文字"
        error={nameError}
      />

      <div className="field">
        <label htmlFor="bio">自己紹介</label>
        <textarea
          id="bio"
          name="bio"
          value={bio}
          aria-invalid={bioError !== null ? true : undefined}
          aria-describedby={bioError !== null ? 'bio-error' : undefined}
          onChange={(e) => setBio(e.target.value)}
        />
        <div className={bioRemaining < 0 ? 'counter over' : 'counter'}>残り {bioRemaining}</div>
        {bioError !== null && (
          <div className="error" id="bio-error" role="alert">
            {bioError}
          </div>
        )}
      </div>

      {formError !== null && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <div className="form-actions">
        <Link className="btn btn-outline" to={`/users/${profile.id}`}>
          キャンセル
        </Link>
        <button className="btn" type="submit" disabled={submitting}>
          {submitting ? '保存中…' : '保存'}
        </button>
      </div>
    </form>
  )
}
