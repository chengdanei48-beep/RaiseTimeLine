package com.raisetimeline.service;

import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.entity.Post;
import com.raisetimeline.exception.ForbiddenException;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.exception.PostNotFoundException;
import com.raisetimeline.repository.PostMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostService {

  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 50;

  private static final String INVALID_CURSOR = "cursor が不正です";

  private final PostMapper postMapper;

  public PostService(PostMapper postMapper) {
    this.postMapper = postMapper;
  }

  @Transactional
  public PostResponse create(long userId, String body) {
    Post post = new Post();
    post.setUserId(userId);
    post.setBody(body);
    postMapper.insert(post);
    return PostResponse.from(find(post.getId()));
  }

  public PostResponse get(long postId) {
    return PostResponse.from(find(postId));
  }

  @Transactional
  public PostResponse update(long userId, long postId, String body) {
    requireOwner(userId, find(postId));
    postMapper.updateBody(postId, body);
    return PostResponse.from(find(postId));
  }

  @Transactional
  public void delete(long userId, long postId) {
    requireOwner(userId, find(postId));
    postMapper.softDelete(postId);
  }

  public TimelineResponse timeline(String cursor, Integer limit) {
    int size = limit == null ? DEFAULT_LIMIT : limit;
    if (size < 1 || size > MAX_LIMIT) {
      throw new InvalidRequestException("limit は1〜" + MAX_LIMIT + "で指定してください");
    }
    Instant cursorCreatedAt = null;
    Long cursorId = null;
    if (cursor != null && !cursor.isEmpty()) {
      String[] parts = decodeCursor(cursor);
      try {
        cursorCreatedAt = Instant.parse(parts[0]);
        cursorId = Long.valueOf(parts[1]);
      } catch (RuntimeException e) {
        throw new InvalidRequestException(INVALID_CURSOR);
      }
    }
    // 次ページの有無を判定するため 1 件多く取る
    List<Post> rows = postMapper.findTimeline(cursorCreatedAt, cursorId, size + 1);
    boolean hasNext = rows.size() > size;
    List<Post> page = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new TimelineResponse(page.stream().map(PostResponse::from).toList(), nextCursor);
  }

  private Post find(long postId) {
    return postMapper.findById(postId).orElseThrow(PostNotFoundException::new);
  }

  private static void requireOwner(long userId, Post post) {
    if (post.getUserId() != userId) {
      throw new ForbiddenException();
    }
  }

  /** 不透明な文字列。中身は「作成日時_ID」(最後に返した投稿の位置)。 */
  private static String encodeCursor(Post last) {
    String raw = last.getCreatedAt() + "_" + last.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String[] decodeCursor(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("_", 2);
      if (parts.length != 2) {
        throw new InvalidRequestException(INVALID_CURSOR);
      }
      return parts;
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(INVALID_CURSOR);
    }
  }
}
