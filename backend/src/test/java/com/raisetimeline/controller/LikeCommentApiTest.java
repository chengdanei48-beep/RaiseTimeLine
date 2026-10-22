package com.raisetimeline.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
class LikeCommentApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String alice;
  private String bob;
  private long postId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    alice = registerAndGetToken("alice", "アリス");
    bob = registerAndGetToken("bob", "ボブ");
    postId = createPost(alice, "こんにちは");
  }

  // ---- いいね ----

  @Test
  void いいねすると件数が増え自分の分はlikedByMeになる() throws Exception {
    like(bob, postId).andExpect(status().isNoContent());

    getPost(bob, postId)
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.likedByMe").value(true));
    getPost(alice, postId)
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.likedByMe").value(false));
  }

  @Test
  void いいねは冪等で2回送っても1件のまま() throws Exception {
    like(bob, postId).andExpect(status().isNoContent());
    like(bob, postId).andExpect(status().isNoContent());

    getPost(bob, postId).andExpect(jsonPath("$.likeCount").value(1));
  }

  @Test
  void 自分の投稿にもいいねできる() throws Exception {
    like(alice, postId).andExpect(status().isNoContent());

    getPost(alice, postId)
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.likedByMe").value(true));
  }

  @Test
  void 取り消すと件数が戻り未いいねの取り消しも204() throws Exception {
    like(bob, postId);
    unlike(bob, postId).andExpect(status().isNoContent());
    unlike(bob, postId).andExpect(status().isNoContent());

    getPost(bob, postId)
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false));
  }

  @Test
  void 存在しない投稿や削除済みの投稿へのいいねは404() throws Exception {
    like(bob, 999999).andExpect(status().isNotFound());
    unlike(bob, 999999).andExpect(status().isNotFound());

    mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", "Bearer " + alice));
    like(bob, postId).andExpect(status().isNotFound());
  }

  @Test
  void 未認証ではいいねできない() throws Exception {
    mockMvc.perform(put("/api/posts/" + postId + "/like")).andExpect(status().isUnauthorized());
  }

  @Test
  void タイムラインに件数といいね済みが入る() throws Exception {
    like(bob, postId);
    comment(alice, postId, "c1");
    comment(bob, postId, "c2");

    mockMvc
        .perform(get("/api/timeline").header("Authorization", "Bearer " + bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].likeCount").value(1))
        .andExpect(jsonPath("$.items[0].commentCount").value(2))
        .andExpect(jsonPath("$.items[0].likedByMe").value(true))
        .andExpect(jsonPath("$.items[0].impressionCount").doesNotExist());
  }

  // ---- コメント ----

  @Test
  void 他人の投稿にコメントすると201で投稿者つきで返る() throws Exception {
    comment(bob, postId, "  よろしく  ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.body").value("よろしく"))
        .andExpect(jsonPath("$.author.username").value("bob"));
  }

  @Test
  void 空の本文と281文字は400で280文字は201() throws Exception {
    comment(bob, postId, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.body").exists());
    comment(bob, postId, "あ".repeat(281)).andExpect(status().isBadRequest());
    comment(bob, postId, "あ".repeat(280)).andExpect(status().isCreated());
  }

  @Test
  void コメント一覧は古い順でカーソルページングできる() throws Exception {
    comment(bob, postId, "1つめ");
    comment(alice, postId, "2つめ");
    comment(bob, postId, "3つめ");

    String page1 =
        mockMvc
            .perform(
                get("/api/posts/" + postId + "/comments?limit=2")
                    .header("Authorization", "Bearer " + alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].body").value("1つめ"))
            .andExpect(jsonPath("$.items[1].body").value("2つめ"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(page1, "$.nextCursor");

    mockMvc
        .perform(
            get("/api/posts/" + postId + "/comments?limit=2&cursor=" + cursor)
                .header("Authorization", "Bearer " + alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].body").value("3つめ"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void コメントの不正なcursorやlimitは400() throws Exception {
    mockMvc
        .perform(
            get("/api/posts/" + postId + "/comments?cursor=xxx")
                .header("Authorization", "Bearer " + alice))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/posts/" + postId + "/comments?limit=51")
                .header("Authorization", "Bearer " + alice))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 自分のコメントは削除できて一覧と件数から消える() throws Exception {
    long commentId = commentAndGetId(bob, postId, "消す");
    comment(alice, postId, "残す");

    deleteComment(bob, commentId).andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/posts/" + postId + "/comments").header("Authorization", "Bearer " + alice))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].body").value("残す"));
    getPost(alice, postId).andExpect(jsonPath("$.commentCount").value(1));
  }

  @Test
  void 他人のコメントは403で削除済みの再削除は404() throws Exception {
    long commentId = commentAndGetId(bob, postId, "bobの");

    deleteComment(alice, commentId).andExpect(status().isForbidden());
    deleteComment(bob, commentId).andExpect(status().isNoContent());
    deleteComment(bob, commentId).andExpect(status().isNotFound());
  }

  @Test
  void 投稿が削除されるとコメントの取得と投稿と削除が404() throws Exception {
    long commentId = commentAndGetId(bob, postId, "c");
    mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", "Bearer " + alice));

    comment(bob, postId, "x").andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/posts/" + postId + "/comments").header("Authorization", "Bearer " + bob))
        .andExpect(status().isNotFound());
    deleteComment(bob, commentId).andExpect(status().isNotFound());
  }

  @Test
  void 未認証ではコメントできない() throws Exception {
    mockMvc
        .perform(
            post("/api/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ---- ヘルパー ----

  private ResultActions like(String token, long id) throws Exception {
    return mockMvc.perform(
        put("/api/posts/" + id + "/like").header("Authorization", bearer(token)));
  }

  private ResultActions unlike(String token, long id) throws Exception {
    return mockMvc.perform(
        delete("/api/posts/" + id + "/like").header("Authorization", bearer(token)));
  }

  private ResultActions getPost(String token, long id) throws Exception {
    return mockMvc
        .perform(get("/api/posts/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  private ResultActions comment(String token, long id, String body) throws Exception {
    return mockMvc.perform(
        post("/api/posts/" + id + "/comments")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":\"" + body + "\"}"));
  }

  private long commentAndGetId(String token, long id, String body) throws Exception {
    String response =
        comment(token, id, body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Number>read(response, "$.id").longValue();
  }

  private ResultActions deleteComment(String token, long commentId) throws Exception {
    return mockMvc.perform(
        delete("/api/comments/" + commentId).header("Authorization", bearer(token)));
  }

  private long createPost(String token, String body) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/posts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Number>read(response, "$.id").longValue();
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
