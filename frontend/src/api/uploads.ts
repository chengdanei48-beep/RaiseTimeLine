import { ApiError, authRequest, GENERIC_ERROR } from './client'
import type { Presign, UploadPurpose } from './types'

export function presignUpload(
  purpose: UploadPurpose,
  contentType: string,
  size: number,
): Promise<Presign> {
  return authRequest<Presign>('/api/uploads/presign', 'POST', { purpose, contentType, size })
}

/**
 * 画像を署名付き URL へ直接 PUT する(API サーバーは通らない)。
 * 認証ヘッダーは付けない(S3 に渡さない)。失敗は ApiError(status 0 は通信失敗)。
 */
export async function putToStorage(presign: Presign, file: File): Promise<void> {
  let res: Response
  try {
    res = await fetch(presign.uploadUrl, {
      method: 'PUT',
      headers: presign.requiredHeaders,
      body: file,
    })
  } catch {
    throw new ApiError(0, GENERIC_ERROR)
  }
  if (!res.ok) {
    throw new ApiError(res.status, '画像のアップロードに失敗しました')
  }
}

/** 画像を順番に署名付き URL へアップロードし、オブジェクトキーを(渡した順に)返す。1 枚でも失敗したら例外。 */
export async function uploadImages(purpose: UploadPurpose, files: File[]): Promise<string[]> {
  const keys: string[] = []
  for (const file of files) {
    const presign = await presignUpload(purpose, file.type, file.size)
    await putToStorage(presign, file)
    keys.push(presign.objectKey)
  }
  return keys
}
