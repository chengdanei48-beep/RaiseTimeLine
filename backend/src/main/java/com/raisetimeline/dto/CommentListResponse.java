package com.raisetimeline.dto;

import java.util.List;

/** nextCursor は次ページが無いとき null。 */
public record CommentListResponse(List<CommentResponse> items, String nextCursor) {

  public CommentListResponse {
    items = List.copyOf(items);
  }
}
