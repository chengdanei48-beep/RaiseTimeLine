package com.raisetimeline.service;

import com.raisetimeline.dto.CommentListResponse;
import com.raisetimeline.dto.CommentResponse;
import com.raisetimeline.entity.Comment;
import com.raisetimeline.exception.CommentNotFoundException;
import com.raisetimeline.exception.ForbiddenException;
import com.raisetimeline.exception.PostNotFoundException;
import com.raisetimeline.repository.CommentMapper;
import com.raisetimeline.repository.PostMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {

  private final CommentMapper commentMapper;
  private final PostMapper postMapper;

  public CommentService(CommentMapper commentMapper, PostMapper postMapper) {
    this.commentMapper = commentMapper;
    this.postMapper = postMapper;
  }

  @Transactional
  public CommentResponse create(long userId, long postId, String body) {
    requirePost(postId);
    Comment comment = new Comment();
    comment.setPostId(postId);
    comment.setUserId(userId);
    comment.setBody(body);
    commentMapper.insert(comment);
    return CommentResponse.from(find(comment.getId()));
  }

  public CommentListResponse list(long postId, String cursor, Integer limit) {
    requirePost(postId);
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    // 次ページの有無を判定するため 1 件多く取る
    List<Comment> rows =
        commentMapper.findByPost(
            postId,
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    boolean hasNext = rows.size() > size;
    List<Comment> page = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = null;
    if (hasNext) {
      Comment last = page.get(page.size() - 1);
      nextCursor = CursorPaging.encode(last.getCreatedAt(), last.getId());
    }
    return new CommentListResponse(page.stream().map(CommentResponse::from).toList(), nextCursor);
  }

  @Transactional
  public void delete(long userId, long commentId) {
    Comment comment = find(commentId);
    if (comment.getUserId() != userId) {
      throw new ForbiddenException();
    }
    commentMapper.softDelete(commentId);
  }

  private void requirePost(long postId) {
    postMapper.findById(postId).orElseThrow(PostNotFoundException::new);
  }

  private Comment find(long commentId) {
    return commentMapper.findById(commentId).orElseThrow(CommentNotFoundException::new);
  }
}
