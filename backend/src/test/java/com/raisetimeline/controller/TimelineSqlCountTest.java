package com.raisetimeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
import com.raisetimeline.support.SqlCountConfig;
import com.raisetimeline.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** タイムライン取得の SQL 発行数が投稿数に比例しないこと(N+1 がないこと)の検証。 */
@ImportTestcontainers(PostgresTestContainer.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
@Import({SqlCountConfig.class, FakeStorageConfig.class})
class TimelineSqlCountTest {

  /** 1 ページの取得で許容する SQL の上限。機能追加で増やすときは根拠を残す。 */
  private static final int MAX_SQL_PER_PAGE = 6;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String token;
  private long userId;
  private int followerSeq;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    String response = TestUsers.register(mockMvc, "alice", "アリス");
    token = TestUsers.accessToken(response);
    userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'alice'", Long.class);
  }

  @Test
  void タイムライン取得のSQL発行数は投稿数に依らず一定で上限以下() throws Exception {
    insertPosts(5);
    int withFew = sqlCountOfTimeline(5);

    insertPosts(40);
    int withMany = sqlCountOfTimeline(20);

    assertThat(withMany).as("5件取得: %d / 20件取得: %d", withFew, withMany).isEqualTo(withFew);
    assertThat(withMany).isLessThanOrEqualTo(MAX_SQL_PER_PAGE);
  }

  @Test
  void フォロワー一覧のSQL発行数は人数に依らず一定で上限以下() throws Exception {
    insertFollowers(3);
    int withFew = sqlCountOf("/api/users/" + userId + "/followers?limit=3", 3);

    insertFollowers(20);
    int withMany = sqlCountOf("/api/users/" + userId + "/followers?limit=20", 20);

    assertThat(withMany).as("3人取得: %d / 20人取得: %d", withFew, withMany).isEqualTo(withFew);
    assertThat(withMany).isLessThanOrEqualTo(MAX_SQL_PER_PAGE);
  }

  @Test
  void ユーザー検索のSQL発行数は結果の人数に依らず一定で上限以下() throws Exception {
    insertFollowers(3);
    int withFew = sqlCountOf("/api/users?q=f&limit=3", 3);

    insertFollowers(20);
    int withMany = sqlCountOf("/api/users?q=f&limit=20", 20);

    assertThat(withMany).as("3人取得: %d / 20人取得: %d", withFew, withMany).isEqualTo(withFew);
    assertThat(withMany).isLessThanOrEqualTo(MAX_SQL_PER_PAGE);
  }

  @Test
  void フォロー中タイムラインのSQL発行数は投稿数に依らず一定で上限以下() throws Exception {
    insertPosts(5);
    int withFew = sqlCountOf("/api/timeline?scope=following&limit=5", 5);

    insertPosts(40);
    int withMany = sqlCountOf("/api/timeline?scope=following&limit=20", 20);

    assertThat(withMany).as("5件取得: %d / 20件取得: %d", withFew, withMany).isEqualTo(withFew);
    assertThat(withMany).isLessThanOrEqualTo(MAX_SQL_PER_PAGE);
  }

  /** 自分をフォローする人を count 人増やす。 */
  private void insertFollowers(int count) {
    for (int i = 0; i < count; i++) {
      Long followerId =
          jdbcTemplate.queryForObject(
              "INSERT INTO users (username, password_hash, display_name) VALUES (?, 'x', 'f') RETURNING id",
              Long.class,
              "f" + followerSeq++);
      jdbcTemplate.update(
          "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", followerId, userId);
    }
  }

  private void insertPosts(int count) {
    for (int i = 0; i < count; i++) {
      Long postId =
          jdbcTemplate.queryForObject(
              "INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id",
              Long.class,
              userId,
              "投稿" + i);
      jdbcTemplate.update("INSERT INTO likes (user_id, post_id) VALUES (?, ?)", userId, postId);
      jdbcTemplate.update(
          "INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, ?)", postId, userId, "c");
      // 画像も投稿ごとに複数枚(取得は投稿 ID 群に対して 1 クエリ)
      for (int order = 0; order < 3; order++) {
        jdbcTemplate.update(
            "INSERT INTO post_images (post_id, object_key, sort_order) VALUES (?, ?, ?)",
            postId,
            "posts/" + userId + "/img-" + postId + "-" + order + ".png",
            order);
      }
    }
  }

  /** limit 件ちょうど返る状態で呼ぶこと。 */
  private int sqlCountOfTimeline(int limit) throws Exception {
    return sqlCountOf("/api/timeline?limit=" + limit, limit);
  }

  /** path を呼び、items がちょうど expectedItems 件のときの SQL 発行数を返す。 */
  private int sqlCountOf(String path, int expectedItems) throws Exception {
    SqlCountConfig.COUNT.set(0);
    String response =
        mockMvc
            .perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<java.util.List<?>>read(response, "$.items")).hasSize(expectedItems);
    return SqlCountConfig.COUNT.get();
  }
}
