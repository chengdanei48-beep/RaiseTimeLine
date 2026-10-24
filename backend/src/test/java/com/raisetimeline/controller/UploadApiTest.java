package com.raisetimeline.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
import com.raisetimeline.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class UploadApiTest {

  private static final long MB = 1024 * 1024;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String token;
  private long userId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    String response = TestUsers.register(mockMvc, "alice");
    token = TestUsers.accessToken(response);
    userId = TestUsers.userId(response);
  }

  @Test
  void アイコン用の署名付きURLをサーバーが決めたキーで発行する() throws Exception {
    presign("AVATAR", "image/png", 1000)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.objectKey")
                .value(matchesPattern("avatars/" + userId + "/[0-9a-f-]{36}\\.png")))
        .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
        .andExpect(jsonPath("$.expiresIn").value(300))
        .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value("image/png"));
  }

  @Test
  void 投稿画像用はpostsの配下にキーを作る() throws Exception {
    presign("POST", "image/jpeg", 1000)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.objectKey")
                .value(matchesPattern("posts/" + userId + "/[0-9a-f-]{36}\\.jpg")));
  }

  @Test
  void キーは毎回異なる() throws Exception {
    String a = key(presign("AVATAR", "image/png", 1));
    String b = key(presign("AVATAR", "image/png", 1));
    org.assertj.core.api.Assertions.assertThat(a).isNotEqualTo(b);
  }

  @ParameterizedTest
  @ValueSource(strings = {"image/jpeg", "image/png", "image/gif", "image/webp"})
  void 許可された形式は発行できる(String contentType) throws Exception {
    presign("AVATAR", contentType, 1000).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"image/svg+xml", "image/bmp", "text/html", "application/pdf", "IMAGE/PNG"})
  void 許可されていない形式は400(String contentType) throws Exception {
    presign("AVATAR", contentType, 1000).andExpect(status().isBadRequest());
  }

  @Test
  void サイズの上限はアイコン2MB投稿画像5MB() throws Exception {
    presign("AVATAR", "image/png", 2 * MB).andExpect(status().isOk());
    presign("AVATAR", "image/png", 2 * MB + 1).andExpect(status().isBadRequest());
    presign("POST", "image/png", 5 * MB).andExpect(status().isOk());
    presign("POST", "image/png", 5 * MB + 1).andExpect(status().isBadRequest());
  }

  @Test
  void サイズが0以下や未指定とpurposeの不正は400() throws Exception {
    presign("AVATAR", "image/png", 0).andExpect(status().isBadRequest());
    presign("AVATAR", "image/png", -1).andExpect(status().isBadRequest());
    send("{\"purpose\":\"AVATAR\",\"contentType\":\"image/png\"}")
        .andExpect(status().isBadRequest());
    send("{\"contentType\":\"image/png\",\"size\":1}").andExpect(status().isBadRequest());
    send("{\"purpose\":\"OTHER\",\"contentType\":\"image/png\",\"size\":1}")
        .andExpect(status().isBadRequest());
    send("{\"purpose\":\"AVATAR\",\"size\":1}").andExpect(status().isBadRequest());
  }

  @Test
  void 未認証では発行できない() throws Exception {
    mockMvc
        .perform(
            post("/api/uploads/presign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"AVATAR\",\"contentType\":\"image/png\",\"size\":1}"))
        .andExpect(status().isUnauthorized());
  }

  // ---- ヘルパー ----

  private ResultActions presign(String purpose, String contentType, long size) throws Exception {
    return send(
        "{\"purpose\":\""
            + purpose
            + "\",\"contentType\":\""
            + contentType
            + "\",\"size\":"
            + size
            + "}");
  }

  private ResultActions send(String json) throws Exception {
    return mockMvc.perform(
        post("/api/uploads/presign")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private static String key(ResultActions actions) throws Exception {
    return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.objectKey");
  }
}
