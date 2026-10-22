package com.raisetimeline.service;

import com.raisetimeline.exception.PostNotFoundException;
import com.raisetimeline.repository.LikeMapper;
import com.raisetimeline.repository.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LikeService {

  private final LikeMapper likeMapper;
  private final PostMapper postMapper;

  public LikeService(LikeMapper likeMapper, PostMapper postMapper) {
    this.likeMapper = likeMapper;
    this.postMapper = postMapper;
  }

  /** 冪等。すでにいいね済みでも何もしない。 */
  @Transactional
  public void like(long userId, long postId) {
    requirePost(postId);
    likeMapper.insert(userId, postId);
  }

  /** 冪等。いいねしていなくても何もしない。 */
  @Transactional
  public void unlike(long userId, long postId) {
    requirePost(postId);
    likeMapper.delete(userId, postId);
  }

  private void requirePost(long postId) {
    postMapper.findById(postId).orElseThrow(PostNotFoundException::new);
  }
}
