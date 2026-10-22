package com.raisetimeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.SqlCountConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** タイムライン取得の SQL 発行数が投稿数に比例しないこと(N+1 がないこと)の検証。 */
@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
@Import(SqlCountConfig.class)
class TimelineSqlCountTest {

  /** 1 ページの取得で許容する SQL の上限。機能追加で増やすときは根拠を残す。 */
  private static final int MAX_SQL_PER_PAGE = 6;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String token;
  private long userId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    String response =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"alice\",\"displayName\":\"アリス\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    token = JsonPath.read(response, "$.accessToken");
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
    }
  }

  /** limit 件ちょうど返る状態で呼ぶこと。 */
  private int sqlCountOfTimeline(int limit) throws Exception {
    SqlCountConfig.COUNT.set(0);
    String response =
        mockMvc
            .perform(get("/api/timeline?limit=" + limit).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<java.util.List<?>>read(response, "$.items")).hasSize(limit);
    return SqlCountConfig.COUNT.get();
  }
}
