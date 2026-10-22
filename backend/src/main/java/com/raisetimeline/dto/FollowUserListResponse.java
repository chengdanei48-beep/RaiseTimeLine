package com.raisetimeline.dto;

import java.util.List;

/** nextCursor は次ページが無いとき null。 */
public record FollowUserListResponse(List<FollowUserResponse> items, String nextCursor) {

  public FollowUserListResponse {
    items = List.copyOf(items);
  }
}
