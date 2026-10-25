package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "ユーザーの一覧(ページング)。フォロー中・フォロワー・検索で共通")
public record FollowUserListResponse(
    @Schema(description = "ユーザー") List<FollowUserResponse> items,
    @Schema(description = "次ページを取る `cursor`。次ページが無いとき null") String nextCursor) {

  public FollowUserListResponse {
    items = List.copyOf(items);
  }
}
