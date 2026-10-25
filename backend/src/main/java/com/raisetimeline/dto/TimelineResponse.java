package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "投稿の一覧(ページング)。タイムライン・ユーザー別の投稿で共通")
public record TimelineResponse(
    @Schema(description = "投稿(新しい順)") List<PostResponse> items,
    @Schema(description = "次ページを取る `cursor`。次ページが無いとき null") String nextCursor) {

  public TimelineResponse {
    items = List.copyOf(items);
  }
}
