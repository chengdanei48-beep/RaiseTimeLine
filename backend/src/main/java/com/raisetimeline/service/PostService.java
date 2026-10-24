package com.raisetimeline.service;

import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.PresignRequest.Purpose;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.entity.Post;
import com.raisetimeline.exception.ForbiddenException;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.exception.PostNotFoundException;
import com.raisetimeline.exception.UserNotFoundException;
import com.raisetimeline.repository.PostMapper;
import com.raisetimeline.repository.UserMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class PostService {

  /** 1 つの投稿に付けられる画像の最大枚数。 */
  public static final int MAX_IMAGES = 4;

  private final PostMapper postMapper;
  private final PostCardAssembler assembler;
  private final UserMapper userMapper;
  private final UploadService uploadService;
  private final TransactionTemplate tx;

  public PostService(
      PostMapper postMapper,
      PostCardAssembler assembler,
      UserMapper userMapper,
      UploadService uploadService,
      TransactionTemplate tx) {
    this.postMapper = postMapper;
    this.assembler = assembler;
    this.userMapper = userMapper;
    this.uploadService = uploadService;
    this.tx = tx;
  }

  /**
   * 投稿を作る。画像は、自分がアップロード済みのものだけ(重複なし)。S3 への確認はネットワーク通信なので、 DB トランザクションの外で先に行い(接続を握らない)、投稿と画像の保存だけを 1
   * つの短いトランザクションにする。
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PostResponse create(long userId, String body, List<String> imageKeys) {
    if (imageKeys.size() > MAX_IMAGES) {
      throw new InvalidRequestException("画像は" + MAX_IMAGES + "枚までです");
    }
    if (new HashSet<>(imageKeys).size() != imageKeys.size()) {
      throw new InvalidRequestException("同じ画像は複数指定できません");
    }
    imageKeys.forEach(key -> uploadService.verifyUploaded(userId, Purpose.POST, key));

    Long postId = tx.execute(status -> savePost(userId, body, imageKeys));
    return assembler.assembleOne(find(Objects.requireNonNull(postId)), userId);
  }

  /** 投稿と画像を保存する(トランザクションの中で呼ぶ)。作った投稿の ID を返す。 */
  private long savePost(long userId, String body, List<String> imageKeys) {
    Post post = new Post();
    post.setUserId(userId);
    post.setBody(body);
    postMapper.insert(post);
    for (int i = 0; i < imageKeys.size(); i++) {
      postMapper.insertImage(post.getId(), imageKeys.get(i), i);
    }
    return post.getId();
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

  /** scope は following(自分+フォロー中)か all(全体)。省略時は all。 */
  public TimelineResponse timeline(long viewerId, String scope, String cursor, Integer limit) {
    boolean followingOnly = parseFollowingOnly(scope);
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    // 次ページの有無を判定するため 1 件多く取る
    List<Post> rows =
        postMapper.findTimeline(
            viewerId,
            followingOnly,
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    return toPage(rows, size, viewerId);
  }

  /** 特定ユーザーの投稿。形式・並び・ページングはタイムラインと同じ。 */
  public TimelineResponse userPosts(long viewerId, long userId, String cursor, Integer limit) {
    if (!userMapper.existsById(userId)) {
      throw new UserNotFoundException();
    }
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    List<Post> rows =
        postMapper.findByUser(
            userId,
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    return toPage(rows, size, viewerId);
  }

  private static boolean parseFollowingOnly(String scope) {
    if (scope == null || scope.equals("all")) {
      return false;
    }
    if (scope.equals("following")) {
      return true;
    }
    throw new InvalidRequestException("scope は following か all で指定してください");
  }

  private TimelineResponse toPage(List<Post> rows, int size, long viewerId) {
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
