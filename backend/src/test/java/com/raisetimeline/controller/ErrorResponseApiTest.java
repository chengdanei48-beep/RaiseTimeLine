package com.raisetimeline.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
import com.raisetimeline.support.TestUsers;
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

/** 共通のエラー応答(日本語の汎用文言・内部の名前を出さない)と、自分のプロフィール取得。 */
@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
@ImportTestcontainers(PostgresTestContainer.class)
@Import(FakeStorageConfig.class)
class ErrorResponseApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String token;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    token = TestUsers.registerAndGetToken(mockMvc, "alice", "アリス");
  }

  @Test
  void パスの値の型が違うときは日本語の汎用文言で内部の名前を出さない() throws Exception {
    for (String path :
        new String[] {"/api/users/abc", "/api/posts/abc", "/api/timeline?limit=abc"}) {
      mockMvc
          .perform(get(path).header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("リクエストの形式が正しくありません"));
    }
  }

  @Test
  void 本文が読めないときは日本語の汎用文言() throws Exception {
    for (String body : new String[] {"{broken", "", "{\"body\":{\"a\":1}}"}) {
      mockMvc
          .perform(
              post("/api/posts")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.detail")
                  .value(
                      org.hamcrest.Matchers.anyOf(
                          org.hamcrest.Matchers.is("リクエストの内容を読み取れません"),
                          org.hamcrest.Matchers.is("入力内容に誤りがあります"))));
    }
  }

  @Test
  void 自分のプロフィールをGETで取得できる() throws Exception {
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.displayName").value("アリス"))
        .andExpect(jsonPath("$.isMe").value(true))
        .andExpect(jsonPath("$.followedByMe").value(false));
  }

  @Test
  void 自分のプロフィールの取得は未認証では401() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void 署名付きURLの発行は上限を超えると429でRetryAfterが付く() throws Exception {
    String json = "{\"purpose\":\"AVATAR\",\"contentType\":\"image/png\",\"size\":10}";
    for (int i = 0; i < com.raisetimeline.service.PresignRateLimiter.LIMIT_PER_MINUTE; i++) {
      mockMvc
          .perform(
              post("/api/uploads/presign")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(
            post("/api/uploads/presign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.detail").value("操作が多すぎます。しばらくしてから再度お試しください"));

    // 他のユーザーには影響しない
    String bob = TestUsers.registerAndGetToken(mockMvc, "bob", "ボブ");
    mockMvc
        .perform(
            post("/api/uploads/presign")
                .header("Authorization", "Bearer " + bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk());
  }
}
