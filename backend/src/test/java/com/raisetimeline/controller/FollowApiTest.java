package com.raisetimeline.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.PostgresTestContainer;
import com.raisetimeline.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ImportTestcontainers(PostgresTestContainer.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
class FollowApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String alice;
  private String bob;
  private String carol;
  private long aliceId;
  private long bobId;
  private long carolId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    String a = TestUsers.register(mockMvc, "alice", "アリス");
    String b = TestUsers.register(mockMvc, "bob", "ボブ");
    String c = TestUsers.register(mockMvc, "carol", "キャロル");
    alice = JsonPath.read(a, "$.accessToken");
    bob = JsonPath.read(b, "$.accessToken");
    carol = JsonPath.read(c, "$.accessToken");
    aliceId = JsonPath.<Number>read(a, "$.user.id").longValue();
    bobId = JsonPath.<Number>read(b, "$.user.id").longValue();
    carolId = JsonPath.<Number>read(c, "$.user.id").longValue();
  }

  // ---- フォロー・解除 ----

  @Test
  void フォローするとプロフィールの件数とfollowedByMeに反映される() throws Exception {
    follow(alice, bobId).andExpect(status().isNoContent());

    profile(alice, bobId)
        .andExpect(jsonPath("$.followerCount").value(1))
        .andExpect(jsonPath("$.followingCount").value(0))
        .andExpect(jsonPath("$.followedByMe").value(true))
        .andExpect(jsonPath("$.isMe").value(false));
    profile(alice, aliceId)
        .andExpect(jsonPath("$.followingCount").value(1))
        .andExpect(jsonPath("$.isMe").value(true))
        .andExpect(jsonPath("$.followedByMe").value(false));
    profile(carol, bobId).andExpect(jsonPath("$.followedByMe").value(false));
  }

  @Test
  void フォローは冪等で2回送っても1件のまま() throws Exception {
    follow(alice, bobId).andExpect(status().isNoContent());
    follow(alice, bobId).andExpect(status().isNoContent());

    profile(alice, bobId).andExpect(jsonPath("$.followerCount").value(1));
  }

  @Test
  void 解除すると件数が戻り未フォローの解除も204() throws Exception {
    follow(alice, bobId);
    unfollow(alice, bobId).andExpect(status().isNoContent());
    unfollow(alice, bobId).andExpect(status().isNoContent());

    profile(alice, bobId)
        .andExpect(jsonPath("$.followerCount").value(0))
        .andExpect(jsonPath("$.followedByMe").value(false));
  }

  @Test
  void 自分自身のフォローは400() throws Exception {
    follow(alice, aliceId).andExpect(status().isBadRequest());
  }

  @Test
  void 存在しないユーザーのフォロー解除とプロフィールは404() throws Exception {
    follow(alice, 999999).andExpect(status().isNotFound());
    unfollow(alice, 999999).andExpect(status().isNotFound());
    profile(alice, 999999).andExpect(status().isNotFound());
  }

  @Test
  void 未認証ではフォローできない() throws Exception {
    mockMvc.perform(put("/api/users/" + bobId + "/follow")).andExpect(status().isUnauthorized());
  }

  // ---- 一覧 ----

  @Test
  void フォロー中とフォロワーの一覧が新しい順で返りfollowedByMeが付く() throws Exception {
    follow(alice, bobId);
    follow(alice, carolId);
    follow(carol, bobId);
    follow(bob, aliceId);

    // alice のフォロー中: carol(新しい) → bob。viewer=bob は bob(自分)=null、carol=false
    mockMvc
        .perform(get("/api/users/" + aliceId + "/following").header("Authorization", bearer(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].username").value("carol"))
        .andExpect(jsonPath("$.items[0].followedByMe").value(false))
        .andExpect(jsonPath("$.items[1].username").value("bob"))
        .andExpect(jsonPath("$.items[1].followedByMe").doesNotExist());

    // bob のフォロワー: carol(新しい) → alice。viewer=alice は alice(自分)=null、carol=false
    mockMvc
        .perform(get("/api/users/" + bobId + "/followers").header("Authorization", bearer(alice)))
        .andExpect(jsonPath("$.items[0].username").value("carol"))
        .andExpect(jsonPath("$.items[0].followedByMe").value(true))
        .andExpect(jsonPath("$.items[1].username").value("alice"))
        .andExpect(jsonPath("$.items[1].followedByMe").doesNotExist());
  }

  @Test
  void 一覧はカーソルでページングできる() throws Exception {
    follow(alice, bobId);
    follow(alice, carolId);

    String page1 =
        mockMvc
            .perform(
                get("/api/users/" + aliceId + "/following?limit=1")
                    .header("Authorization", bearer(alice)))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].username").value("carol"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(page1, "$.nextCursor");

    mockMvc
        .perform(
            get("/api/users/" + aliceId + "/following?limit=1&cursor=" + cursor)
                .header("Authorization", bearer(alice)))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].username").value("bob"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void 存在しないユーザーの一覧は404で不正なlimitは400() throws Exception {
    mockMvc
        .perform(get("/api/users/999999/following").header("Authorization", bearer(alice)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/users/" + aliceId + "/followers?limit=51")
                .header("Authorization", bearer(alice)))
        .andExpect(status().isBadRequest());
  }

  // ---- タイムライン・投稿一覧 ----

  @Test
  void フォロー中タイムラインは自分とフォロー中の投稿だけで解除すると消える() throws Exception {
    createPost(alice, "aliceの");
    createPost(bob, "bobの");
    createPost(carol, "carolの");

    // フォロー 0 人でも自分の投稿は出る
    timeline(alice, "following").andExpect(jsonPath("$.items.length()").value(1));

    follow(alice, bobId);
    timeline(alice, "following")
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].body").value("bobの"))
        .andExpect(jsonPath("$.items[1].body").value("aliceの"));
    timeline(alice, "all").andExpect(jsonPath("$.items.length()").value(3));

    unfollow(alice, bobId);
    timeline(alice, "following").andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void 不正なscopeは400() throws Exception {
    timeline(alice, "xxx").andExpect(status().isBadRequest());
  }

  @Test
  void ユーザー別の投稿一覧はそのユーザーの投稿だけで投稿数に反映される() throws Exception {
    createPost(alice, "a1");
    createPost(bob, "b1");
    createPost(bob, "b2");

    mockMvc
        .perform(get("/api/users/" + bobId + "/posts").header("Authorization", bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].body").value("b2"));
    profile(alice, bobId).andExpect(jsonPath("$.postCount").value(2));
    mockMvc
        .perform(get("/api/users/999999/posts").header("Authorization", bearer(alice)))
        .andExpect(status().isNotFound());
  }

  // ---- ヘルパー ----

  private ResultActions follow(String token, long id) throws Exception {
    return mockMvc.perform(
        put("/api/users/" + id + "/follow").header("Authorization", bearer(token)));
  }

  private ResultActions unfollow(String token, long id) throws Exception {
    return mockMvc.perform(
        delete("/api/users/" + id + "/follow").header("Authorization", bearer(token)));
  }

  private ResultActions profile(String token, long id) throws Exception {
    return mockMvc.perform(get("/api/users/" + id).header("Authorization", bearer(token)));
  }

  private ResultActions timeline(String token, String scope) throws Exception {
    return mockMvc.perform(
        get("/api/timeline?scope=" + scope).header("Authorization", bearer(token)));
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
