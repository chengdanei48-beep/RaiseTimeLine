package com.raisetimeline.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.FakeObjectStorage;
import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
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
class ProfileApiTest {

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
    String a = register("alice", "alice");
    String b = register("bob", "bob");
    alice = JsonPath.read(a, "$.accessToken");
    bob = JsonPath.read(b, "$.accessToken");
    aliceId = JsonPath.<Number>read(a, "$.user.id").longValue();
    bobId = JsonPath.<Number>read(b, "$.user.id").longValue();
  }

  // ---- 表示名・自己紹介 ----

  @Test
  void 表示名と自己紹介を更新できてユーザーIDは変わらない() throws Exception {
    update(alice, "{\"displayName\":\"  アリス  \",\"bio\":\"はじめまして\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.displayName").value("アリス"))
        .andExpect(jsonPath("$.bio").value("はじめまして"))
        .andExpect(jsonPath("$.isMe").value(true));

    // 他の画面(投稿・検索)にも反映される
    createPost(alice, "こんにちは");
    mockMvc
        .perform(get("/api/timeline").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].author.displayName").value("アリス"));
  }

  @Test
  void 自己紹介は空にすると消える() throws Exception {
    update(alice, "{\"displayName\":\"a\",\"bio\":\"あり\"}")
        .andExpect(jsonPath("$.bio").value("あり"));
    update(alice, "{\"displayName\":\"a\",\"bio\":\"  \"}")
        .andExpect(jsonPath("$.bio").doesNotExist());
    update(alice, "{\"displayName\":\"a\",\"bio\":\"あり\"}");
    update(alice, "{\"displayName\":\"a\"}").andExpect(jsonPath("$.bio").doesNotExist());
  }

  @Test
  void 表示名と自己紹介の検証() throws Exception {
    update(alice, "{\"displayName\":\"\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.displayName").exists());
    update(alice, "{\"displayName\":\"   \"}").andExpect(status().isBadRequest());
    update(alice, "{\"bio\":\"x\"}").andExpect(status().isBadRequest());
    update(alice, "{\"displayName\":\"" + "あ".repeat(30) + "\"}").andExpect(status().isOk());
    update(alice, "{\"displayName\":\"" + "あ".repeat(31) + "\"}")
        .andExpect(status().isBadRequest());
    update(alice, "{\"displayName\":\"a\",\"bio\":\"" + "あ".repeat(160) + "\"}")
        .andExpect(status().isOk());
    update(alice, "{\"displayName\":\"a\",\"bio\":\"" + "あ".repeat(161) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.bio").exists());
  }

  @Test
  void ユーザーIDは更新できない() throws Exception {
    update(alice, "{\"displayName\":\"a\",\"username\":\"hacker\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));
  }

  @Test
  void 未認証では更新できない() throws Exception {
    mockMvc
        .perform(
            put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"a\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ---- アイコン ----

  @Test
  void アイコンのS3への確認はDBトランザクションの外で行われる() throws Exception {
    String key = uploaded(aliceId, "image/png", 100);

    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + key + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").isNotEmpty());

    org.assertj.core.api.Assertions.assertThat(storage.statWasCalledInsideTransaction())
        .as("S3 への確認の間、DB 接続を握らない")
        .isFalse();
  }

  @Test
  void アイコンを設定するとあらゆる応答のavatarUrlに入る() throws Exception {
    String key = uploaded(aliceId, "image/png", 1000);

    update(alice, "{\"displayName\":\"alice\",\"avatarKey\":\"" + key + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value("https://fake-s3.test/get/" + key));

    String expected = "https://fake-s3.test/get/" + key;
    mockMvc
        .perform(get("/api/users/" + aliceId).header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.avatarUrl").value(expected));
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", bearer(alice)))
        .andExpect(jsonPath("$.avatarUrl").value(expected));
    createPost(alice, "投稿");
    mockMvc
        .perform(get("/api/timeline").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].author.avatarUrl").value(expected));
    mockMvc
        .perform(put("/api/users/" + aliceId + "/follow").header("Authorization", bearer(bob)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/users/" + bobId + "/following").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].avatarUrl").value(expected));
    mockMvc
        .perform(get("/api/users?q=alice").header("Authorization", bearer(bob)))
        .andExpect(jsonPath("$.items[0].avatarUrl").value(expected));
  }

  @Test
  void avatarKeyを省略すると変更なしでnullだと削除される() throws Exception {
    String key = uploaded(aliceId, "image/webp", 500);
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + key + "\"}")
        .andExpect(jsonPath("$.avatarUrl").isNotEmpty());

    update(alice, "{\"displayName\":\"b\"}").andExpect(jsonPath("$.avatarUrl").isNotEmpty());
    update(alice, "{\"displayName\":\"b\",\"avatarKey\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  @Test
  void 他人のキーや形の違うキーは400() throws Exception {
    String bobsKey = uploaded(bobId, "image/png", 100);
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + bobsKey + "\"}")
        .andExpect(status().isBadRequest());

    for (String bad :
        new String[] {
          "posts/" + aliceId + "/x.png",
          "avatars/" + aliceId + "/../" + bobId + "/x.png",
          "avatars/" + aliceId + "/a/b.png",
          "avatars/" + aliceId + "/",
          "avatars/" + aliceId + "/noext",
          ""
        }) {
      update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + bad + "\"}")
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void アップロードされていないキーは400() throws Exception {
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"avatars/" + aliceId + "/missing.png\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void サイズ超過や画像以外はアイコンにできない() throws Exception {
    String tooBig = uploaded(aliceId, "image/png", 2 * MB + 1);
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + tooBig + "\"}")
        .andExpect(status().isBadRequest());

    String notImage = "avatars/" + aliceId + "/doc.png";
    storage.put(notImage, 100, "text/html");
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + notImage + "\"}")
        .andExpect(status().isBadRequest());

    String ok = uploaded(aliceId, "image/png", 2 * MB);
    update(alice, "{\"displayName\":\"a\",\"avatarKey\":\"" + ok + "\"}")
        .andExpect(status().isOk());
  }

  // ---- ヘルパー ----

  private ResultActions update(String token, String json) throws Exception {
    return mockMvc.perform(
        put("/api/users/me")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** サーバーが発行したのと同じ形のキーで、アップロード済みの状態を作る。 */
  private String uploaded(long userId, String contentType, long size) {
    String key = "avatars/" + userId + "/" + java.util.UUID.randomUUID() + ".png";
    storage.put(key, size, contentType);
    return key;
  }

  private void createPost(String token, String body) throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}"))
        .andExpect(status().isCreated());
  }

  private String register(String username, String displayName) throws Exception {
    return mockMvc
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
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
