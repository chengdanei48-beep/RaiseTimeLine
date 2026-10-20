package com.raisetimeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
class PostApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String alice;
  private String bob;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    alice = registerAndGetToken("alice", "アリス");
    bob = registerAndGetToken("bob", "ボブ");
  }

  // ---- 作成 ----

  @Test
  void 投稿を作成すると201で内容と投稿者が返る() throws Exception {
    createPost(alice, "こんにちは")
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", matchesPattern("/api/posts/\\d+")))
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.body").value("こんにちは"))
        .andExpect(jsonPath("$.author.username").value("alice"))
        .andExpect(jsonPath("$.author.displayName").value("アリス"))
        .andExpect(jsonPath("$.impressionCount").doesNotExist());
  }

  @Test
  void 本文は前後の空白を除いて保存される() throws Exception {
    createPost(alice, "  hello  ").andExpect(jsonPath("$.body").value("hello"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\n\t"})
  void 空の本文は400になる(String body) throws Exception {
    createPost(alice, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.body").exists());
  }

  @Test
  void 本文は280文字まで投稿できて281文字は400になる() throws Exception {
    createPost(alice, "あ".repeat(280)).andExpect(status().isCreated());
    createPost(alice, "あ".repeat(281))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.body").exists());
  }

  @Test
  void 未認証では投稿できない() throws Exception {
    mockMvc
        .perform(
            post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
  }

  // ---- タイムライン ----

  @Test
  void タイムラインは新しい順で全ユーザーの投稿が並ぶ() throws Exception {
    createPost(alice, "1つめ");
    createPost(bob, "2つめ");
    createPost(alice, "3つめ");

    mockMvc
        .perform(get("/api/timeline").header("Authorization", "Bearer " + alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].body").value("3つめ"))
        .andExpect(jsonPath("$.items[1].body").value("2つめ"))
        .andExpect(jsonPath("$.items[1].author.username").value("bob"))
        .andExpect(jsonPath("$.items[2].body").value("1つめ"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void カーソルで重複なく全件をページングできる() throws Exception {
    for (int i = 1; i <= 5; i++) {
      createPost(alice, "投稿" + i);
    }

    String first = timeline(alice, "?limit=2");
    assertThat(JsonPath.<String>read(first, "$.items[0].body")).isEqualTo("投稿5");
    assertThat(JsonPath.<String>read(first, "$.items[1].body")).isEqualTo("投稿4");
    String cursor1 = JsonPath.read(first, "$.nextCursor");

    String second = timeline(alice, "?limit=2&cursor=" + cursor1);
    assertThat(JsonPath.<String>read(second, "$.items[0].body")).isEqualTo("投稿3");
    assertThat(JsonPath.<String>read(second, "$.items[1].body")).isEqualTo("投稿2");
    String cursor2 = JsonPath.read(second, "$.nextCursor");

    String third = timeline(alice, "?limit=2&cursor=" + cursor2);
    assertThat(JsonPath.<Integer>read(third, "$.items.length()")).isEqualTo(1);
    assertThat(JsonPath.<String>read(third, "$.items[0].body")).isEqualTo("投稿1");
    assertThat(JsonPath.<Object>read(third, "$.nextCursor")).isNull();
  }

  @Test
  void ちょうどlimit件のとき次ページは無い() throws Exception {
    createPost(alice, "a");
    createPost(alice, "b");

    String body = timeline(alice, "?limit=2");
    assertThat(JsonPath.<Object>read(body, "$.nextCursor")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"?limit=0", "?limit=51", "?cursor=%21%21%21", "?cursor=YWJj"})
  void 不正なlimitやcursorは400になる(String query) throws Exception {
    mockMvc
        .perform(get("/api/timeline" + query).header("Authorization", "Bearer " + alice))
        .andExpect(status().isBadRequest());
  }

  // ---- 取得 ----

  @Test
  void 投稿を個別に取得できる() throws Exception {
    long id = createPostAndGetId(alice, "個別");

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("個別"));
  }

  // ---- 編集 ----

  @Test
  void 本人は投稿を編集できる() throws Exception {
    long id = createPostAndGetId(alice, "before");

    updatePost(alice, id, " after ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("after"));

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.body").value("after"));
  }

  @Test
  void 編集すると更新日時が作成日時より後になる() throws Exception {
    long id = createPostAndGetId(alice, "before");

    String body = updatePost(alice, id, "after").andReturn().getResponse().getContentAsString();

    assertThat(JsonPath.<String>read(body, "$.updatedAt"))
        .isGreaterThan(JsonPath.<String>read(body, "$.createdAt"));
  }

  @Test
  void 他人の投稿は編集できず403になる() throws Exception {
    long id = createPostAndGetId(alice, "before");

    updatePost(bob, id, "hacked").andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.body").value("before"));
  }

  @Test
  void 編集でも本文の検証が効く() throws Exception {
    long id = createPostAndGetId(alice, "before");

    updatePost(alice, id, "").andExpect(status().isBadRequest());
    updatePost(alice, id, "あ".repeat(281)).andExpect(status().isBadRequest());
  }

  @Test
  void 存在しない投稿の編集は404になる() throws Exception {
    updatePost(alice, 999999, "x").andExpect(status().isNotFound());
  }

  // ---- 削除 ----

  @Test
  void 本人は投稿を削除でき一覧から消える() throws Exception {
    long id = createPostAndGetId(alice, "消す");
    createPost(alice, "残す");

    mockMvc
        .perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/timeline").header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].body").value("残す"));
  }

  @Test
  void 削除は論理削除で行が残る() throws Exception {
    long id = createPostAndGetId(alice, "消す");

    mockMvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + alice));

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM posts WHERE id = ? AND deleted_at IS NOT NULL",
            Integer.class,
            id);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void 他人の投稿は削除できず403になる() throws Exception {
    long id = createPostAndGetId(alice, "守る");

    mockMvc
        .perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + bob))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isOk());
  }

  @Test
  void 削除済みや存在しない投稿の削除と編集は404になる() throws Exception {
    long id = createPostAndGetId(alice, "消す");
    mockMvc.perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + alice));

    mockMvc
        .perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isNotFound());
    updatePost(alice, id, "x").andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/posts/999999").header("Authorization", "Bearer " + alice))
        .andExpect(status().isNotFound());
  }

  // ---- 予約投稿 ----

  @Test
  void 予約投稿は201で予約中として返りタイムラインには出ない() throws Exception {
    createScheduled(alice, "あとで", Instant.now().plus(1, ChronoUnit.HOURS))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scheduled").value(true));
    createPost(alice, "いますぐ").andExpect(jsonPath("$.scheduled").value(false));

    mockMvc
        .perform(get("/api/timeline").header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].body").value("いますぐ"));
  }

  @Test
  void 予約中の投稿は本人の予約一覧にだけ出る() throws Exception {
    Instant later = Instant.now().plus(2, ChronoUnit.HOURS);
    Instant sooner = Instant.now().plus(1, ChronoUnit.HOURS);
    createScheduled(alice, "遅い", later);
    createScheduled(alice, "早い", sooner);
    createScheduled(bob, "ボブの予約", sooner);

    mockMvc
        .perform(get("/api/posts/scheduled").header("Authorization", "Bearer " + alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].body").value("早い"))
        .andExpect(jsonPath("$[1].body").value("遅い"));
  }

  @Test
  void 予約中の投稿は他人には存在しない扱いで本人は取得編集取消できる() throws Exception {
    long id = createScheduledAndGetId(alice, "ひみつ", Instant.now().plus(1, ChronoUnit.HOURS));

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + bob))
        .andExpect(status().isNotFound());
    updatePost(bob, id, "x").andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + bob))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isOk());
    updatePost(alice, id, "更新").andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/posts/" + id).header("Authorization", "Bearer " + alice))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/posts/scheduled").header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 予約時刻を過ぎるとタイムラインに出て予約一覧から消える() throws Exception {
    long id = createScheduledAndGetId(alice, "公開される", Instant.now().plus(1, ChronoUnit.HOURS));

    // 公開日時を過去へ進めて、時刻が来た状態にする
    jdbcTemplate.update(
        "UPDATE posts SET created_at = now() - interval '1 minute' WHERE id = ?", id);

    mockMvc
        .perform(get("/api/timeline").header("Authorization", "Bearer " + bob))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].body").value("公開される"))
        .andExpect(jsonPath("$.items[0].scheduled").value(false));
    mockMvc
        .perform(get("/api/posts/scheduled").header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 予約日時は未来から30日先までしか指定できない() throws Exception {
    createScheduled(alice, "過去", Instant.now().minus(1, ChronoUnit.MINUTES))
        .andExpect(status().isBadRequest());
    createScheduled(alice, "31日先", Instant.now().plus(31, ChronoUnit.DAYS))
        .andExpect(status().isBadRequest());
    createScheduled(alice, "29日先", Instant.now().plus(29, ChronoUnit.DAYS))
        .andExpect(status().isCreated());
  }

  @Test
  void 予約日時の形式が不正なら400になる() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\",\"scheduledAt\":\"あした\"}"))
        .andExpect(status().isBadRequest());
  }

  // ---- ヘルパー ----

  private ResultActions createScheduled(String token, String body, Instant scheduledAt)
      throws Exception {
    return mockMvc.perform(
        post("/api/posts")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"body\":"
                    + JsonPathString.quote(body)
                    + ",\"scheduledAt\":\""
                    + scheduledAt
                    + "\"}"));
  }

  private long createScheduledAndGetId(String token, String body, Instant scheduledAt)
      throws Exception {
    String response =
        createScheduled(token, body, scheduledAt)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Number>read(response, "$.id").longValue();
  }

  private ResultActions createPost(String token, String body) throws Exception {
    return mockMvc.perform(
        post("/api/posts")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":" + JsonPathString.quote(body) + "}"));
  }

  private long createPostAndGetId(String token, String body) throws Exception {
    String response =
        createPost(token, body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Number>read(response, "$.id").longValue();
  }

  private ResultActions updatePost(String token, long id, String body) throws Exception {
    return mockMvc.perform(
        patch("/api/posts/" + id)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":" + JsonPathString.quote(body) + "}"));
  }

  private String timeline(String token, String query) throws Exception {
    return mockMvc
        .perform(get("/api/timeline" + query).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String registerAndGetToken(String username, String displayName) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + username
                            + "\",\"displayName\":\""
                            + displayName
                            + "\",\"password\":\"password123\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.accessToken");
  }

  /** JSON 文字列リテラルへのエスケープ(改行・タブ・引用符のみ対応すれば足りる)。 */
  private static final class JsonPathString {
    private JsonPathString() {}

    static String quote(String s) {
      return "\""
          + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
          + "\"";
    }
  }
}
