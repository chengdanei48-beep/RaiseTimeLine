package com.raisetimeline.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.FakeObjectStorage;
import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
@ImportTestcontainers(PostgresTestContainer.class)
@Import(FakeStorageConfig.class)
class PostImageApiTest {

  private static final long MB = 1024 * 1024;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FakeObjectStorage storage;

  private String alice;
  private String bob;
  private long aliceId;
  private long bobId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    storage.clear();
    String a = register("alice");
    String b = register("bob");
    alice = JsonPath.read(a, "$.accessToken");
    bob = JsonPath.read(b, "$.accessToken");
    aliceId = JsonPath.<Number>read(a, "$.user.id").longValue();
    bobId = JsonPath.<Number>read(b, "$.user.id").longValue();
  }

  @Test
  void 画像つきで投稿でき順序どおりのimagesが返る() throws Exception {
    String k1 = uploaded(aliceId, "image/png", 1000);
    String k2 = uploaded(aliceId, "image/jpeg", 2000);

    createPost(alice, "写真", List.of(k1, k2))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.images.length()").value(2))
        .andExpect(jsonPath("$.images[0].url").value("https://fake-s3.test/get/" + k1))
        .andExpect(jsonPath("$.images[0].sortOrder").value(0))
        .andExpect(jsonPath("$.images[1].url").value("https://fake-s3.test/get/" + k2))
        .andExpect(jsonPath("$.images[1].sortOrder").value(1));
  }

  @Test
  void S3への確認はDBトランザクションの外で行われる() throws Exception {
    String key = uploaded(aliceId, "image/png", 10);

    createPost(alice, "確認", List.of(key)).andExpect(status().isCreated());

    org.assertj.core.api.Assertions.assertThat(storage.statWasCalledInsideTransaction())
        .as("S3 への確認の間、DB 接続を握らない")
        .isFalse();
  }

  @Test
  void 長すぎるキーは400で投稿は作られない() throws Exception {
    String longKey = "posts/" + aliceId + "/" + "a".repeat(300) + ".png";
    storage.put(longKey, 10, "image/png");

    createPost(alice, "長いキー", List.of(longKey)).andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/timeline").header("Authorization", bearer(alice)))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void 画像なしの投稿はimagesが空() throws Exception {
    createPost(alice, "本文だけ", List.of())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.images.length()").value(0));
    // imageKeys を省略しても同じ
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"省略\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.images.length()").value(0));
  }

  @Test
  void 画像は4枚まででき5枚は400() throws Exception {
    List<String> four =
        List.of(
            uploaded(aliceId, "image/png", 1),
            uploaded(aliceId, "image/png", 1),
            uploaded(aliceId, "image/png", 1),
            uploaded(aliceId, "image/png", 1));
    createPost(alice, "4枚", four)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.images.length()").value(4))
        .andExpect(jsonPath("$.images[3].sortOrder").value(3));

    List<String> five = new java.util.ArrayList<>(four);
    five.add(uploaded(aliceId, "image/png", 1));
    createPost(alice, "5枚", five).andExpect(status().isBadRequest());
  }

  @Test
  void 他人のキーや未アップロードや重複は400で投稿は作られない() throws Exception {
    String bobsKey = uploaded(bobId, "image/png", 100);
    createPost(alice, "他人のキー", List.of(bobsKey)).andExpect(status().isBadRequest());

    createPost(alice, "未アップロード", List.of("posts/" + aliceId + "/" + UUID.randomUUID() + ".png"))
        .andExpect(status().isBadRequest());

    String own = uploaded(aliceId, "image/png", 100);
    createPost(alice, "重複", List.of(own, own)).andExpect(status().isBadRequest());

    // アイコン用のキーは投稿画像にできない
    String avatarKey = "avatars/" + aliceId + "/" + UUID.randomUUID() + ".png";
    storage.put(avatarKey, 100, "image/png");
    createPost(alice, "アイコンのキー", List.of(avatarKey)).andExpect(status().isBadRequest());

    for (String bad :
        new String[] {
          "posts/" + aliceId + "/../" + bobId + "/x.png", "posts/" + aliceId + "/x", "", "  "
        }) {
      createPost(alice, "不正", List.of(bad)).andExpect(status().isBadRequest());
    }

    // 失敗した投稿は残らない
    mockMvc
        .perform(get("/api/timeline").header("Authorization", bearer(alice)))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void サイズ超過や画像以外は400() throws Exception {
    String tooBig = uploaded(aliceId, "image/png", 5 * MB + 1);
    createPost(alice, "6MB", List.of(tooBig)).andExpect(status().isBadRequest());

    String pdf = "posts/" + aliceId + "/" + UUID.randomUUID() + ".png";
    storage.put(pdf, 100, "application/pdf");
    createPost(alice, "PDF", List.of(pdf)).andExpect(status().isBadRequest());

    String ok = uploaded(aliceId, "image/png", 5 * MB);
    createPost(alice, "5MB", List.of(ok)).andExpect(status().isCreated());
  }

  @Test
  void 画像のみの投稿は本文が必要() throws Exception {
    createPost(alice, "", List.of(uploaded(aliceId, "image/png", 1)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void タイムライン詳細ユーザー別投稿と編集の応答にもimagesが入る() throws Exception {
    String key = uploaded(aliceId, "image/webp", 10);
    String created =
        createPost(alice, "写真", List.of(key)).andReturn().getResponse().getContentAsString();
    long id = JsonPath.<Number>read(created, "$.id").longValue();
    String expected = "https://fake-s3.test/get/" + key;

    mockMvc
        .perform(get("/api/timeline").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].images[0].url").value(expected));
    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.images[0].url").value(expected));
    mockMvc
        .perform(get("/api/users/" + aliceId + "/posts").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].images[0].url").value(expected));
    // 編集は本文のみで、画像は変わらない
    mockMvc
        .perform(
            patch("/api/posts/" + id)
                .header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"直した\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images.length()").value(1))
        .andExpect(jsonPath("$.images[0].url").value(expected));
  }

  @Test
  void DBにはキーだけが保存されURLは保存されない() throws Exception {
    String key = uploaded(aliceId, "image/png", 10);
    createPost(alice, "x", List.of(key));

    String saved = jdbcTemplate.queryForObject("SELECT object_key FROM post_images", String.class);
    org.assertj.core.api.Assertions.assertThat(saved).isEqualTo(key).doesNotContain("http");
  }

  @Test
  void 投稿を削除すると画像も表示されない() throws Exception {
    String key = uploaded(aliceId, "image/png", 10);
    long id =
        JsonPath.<Number>read(
                createPost(alice, "消す", List.of(key))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.id")
            .longValue();
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/posts/" + id)
                .header("Authorization", bearer(alice)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", bearer(bob)))
        .andExpect(status().isNotFound());
  }

  // ---- ヘルパー ----

  private ResultActions createPost(String token, String body, List<String> keys) throws Exception {
    String keysJson =
        keys.stream().map(k -> "\"" + k + "\"").collect(java.util.stream.Collectors.joining(","));
    return mockMvc.perform(
        post("/api/posts")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":\"" + body + "\",\"imageKeys\":[" + keysJson + "]}"));
  }

  /** サーバーが発行したのと同じ形のキーで、アップロード済みの状態を作る。 */
  private String uploaded(long userId, String contentType, long size) {
    String key = "posts/" + userId + "/" + UUID.randomUUID() + ".png";
    storage.put(key, size, contentType);
    return key;
  }

  private String register(String username) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\""
                        + username
                        + "\",\"displayName\":\""
                        + username
                        + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
