package com.raisetimeline.dto;

import java.util.List;

/** nextCursor は次ページが無いとき null。 */
public record TimelineResponse(List<PostResponse> items, String nextCursor) {

  public TimelineResponse {
    items = List.copyOf(items);
  }
}
