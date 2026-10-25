package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** followedByMe は、自分自身の行では null(フォローボタンを出さない)。 */
@Schema(description = "一覧に出るユーザー(フォロー中・フォロワー・検索結果)")
public record FollowUserResponse(
    @Schema(description = "ユーザーの内部 ID", example = "7") Long id,
    @Schema(description = "ユーザーID(@ハンドル)", example = "hanako") String username,
    @Schema(description = "表示名", example = "佐藤花子") String displayName,
    @Schema(description = "アイコンの表示用の署名付き URL(期限つき)。未設定なら null") String avatarUrl,
    @Schema(description = "自己紹介。未設定なら null", example = "よろしくお願いします") String bio,
    @Schema(description = "自分がフォロー済みか。自分自身の行では null(フォローボタンを出さない)") Boolean followedByMe) {}
