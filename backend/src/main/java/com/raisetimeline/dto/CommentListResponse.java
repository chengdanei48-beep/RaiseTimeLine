package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "コメントの一覧(ページング)")
public record CommentListResponse(
    @Schema(description = "コメント(古い順)") List<CommentResponse> items,
    @Schema(description = "次ページを取る `cursor`。次ページが無いとき null") String nextCursor) {

  public CommentListResponse {
    items = List.copyOf(items);
  }
}
