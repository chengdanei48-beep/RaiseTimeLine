package com.raisetimeline.service;

import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.entity.Post;
import com.raisetimeline.exception.ForbiddenException;
import com.raisetimeline.exception.PostNotFoundException;
import com.raisetimeline.repository.PostMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostService {

  private final PostMapper postMapper;
  private final PostCardAssembler assembler;

  public PostService(PostMapper postMapper, PostCardAssembler assembler) {
    this.postMapper = postMapper;
    this.assembler = assembler;
  }

  @Transactional
  public PostResponse create(long userId, String body) {
    Post post = new Post();
    post.setUserId(userId);
    post.setBody(body);
    postMapper.insert(post);
    return assembler.assembleOne(find(post.getId()), userId);
  }

  public PostResponse get(long viewerId, long postId) {
    return assembler.assembleOne(find(postId), viewerId);
  }

  @Transactional
  public PostResponse update(long userId, long postId, String body) {
    requireOwner(userId, find(postId));
    postMapper.updateBody(postId, body);
    return assembler.assembleOne(find(postId), userId);
  }

  @Transactional
  public void delete(long userId, long postId) {
    requireOwner(userId, find(postId));
    postMapper.softDelete(postId);
  }

  public TimelineResponse timeline(long viewerId, String cursor, Integer limit) {
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    // 次ページの有無を判定するため 1 件多く取る
    List<Post> rows =
        postMapper.findTimeline(
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    boolean hasNext = rows.size() > size;
    List<Post> page = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = null;
    if (hasNext) {
      Post last = page.get(page.size() - 1);
      nextCursor = CursorPaging.encode(last.getCreatedAt(), last.getId());
    }
    return new TimelineResponse(assembler.assemble(page, viewerId), nextCursor);
  }

  private Post find(long postId) {
    return postMapper.findById(postId).orElseThrow(PostNotFoundException::new);
  }

  private static void requireOwner(long userId, Post post) {
    if (post.getUserId() != userId) {
      throw new ForbiddenException();
    }
  }
}
