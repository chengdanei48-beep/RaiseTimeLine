package com.raisetimeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
class AuthApiTest {

  private static final String PASSWORD = "password123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;

  @BeforeEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM users");
  }

  // ---- 登録 ----

  @Test
  void 登録するとトークンが発行されそのトークンでmeを呼べる() throws Exception {
    String body = register("alice", "アリス", PASSWORD).andReturn().getResponse().getContentAsString();

    assertThat(JsonPath.<String>read(body, "$.tokenType")).isEqualTo("Bearer");
    assertThat(JsonPath.<Integer>read(body, "$.expiresIn")).isPositive();
    assertThat(JsonPath.<String>read(body, "$.user.username")).isEqualTo("alice");
    assertThat(body).doesNotContain("password");

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.displayName").value("アリス"))
        .andExpect(jsonPath("$.avatarUrl").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void パスワードはBCryptでハッシュ化されて保存される() throws Exception {
    register("alice", "アリス", PASSWORD);

    String hash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE username = 'alice'", String.class);
    assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
  }

  @Test
  void 表示名は前後の空白を除いて保存される() throws Exception {
    register("alice", "  アリス  ", PASSWORD).andExpect(status().isCreated());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT display_name FROM users WHERE username = 'alice'", String.class))
        .isEqualTo("アリス");
  }

  @Test
  void 大文字小文字違いのユーザーIDは重複として409になり表記は保持される() throws Exception {
    register("Alice", "アリス", PASSWORD).andExpect(status().isCreated());

    register("alice", "別人", PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("このユーザーIDは既に使われています"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT username FROM users WHERE lower(username) = 'alice'", String.class))
        .isEqualTo("Alice");
  }

  @Test
  void 同時登録の競合は片方が201でもう片方が409になる() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> tasks = new ArrayList<>();
      for (String name : List.of("race_user", "RACE_USER")) {
        tasks.add(
            () ->
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson(name, "競合", PASSWORD)))
                    .andReturn()
                    .getResponse()
                    .getStatus());
      }
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> f : pool.invokeAll(tasks)) {
        statuses.add(f.get());
      }
      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab", "abcdefghijklmnop", "ali ce", " alice", "alice ", "アリス", "al-ice"})
  void 不正なユーザーIDは400(String username) throws Exception {
    register(username, "名前", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "abcdefghijklmno", "a_B_c"})
  void ユーザーIDの境界値は登録できる(String username) throws Exception {
    register(username, "名前", PASSWORD).andExpect(status().isCreated());
  }

  @ParameterizedTest
  @MethodSource("invalidDisplayNames")
  void 不正な表示名は400(String displayName) throws Exception {
    register("alice", displayName, PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.displayName").exists());
  }

  @Test
  void 表示名30文字は登録できる() throws Exception {
    register("alice", "あ".repeat(30), PASSWORD).andExpect(status().isCreated());
  }

  @ParameterizedTest
  @MethodSource("invalidPasswords")
  void 不正なパスワードは400(String password) throws Exception {
    // 「あ」25文字は文字数は範囲内だが UTF-8 で 75 バイトになる
    register("alice", "名前", password)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @ParameterizedTest
  @MethodSource("validPasswords")
  void パスワードの境界値は登録できる(String password) throws Exception {
    register("alice", "名前", password).andExpect(status().isCreated());
  }

  @Test
  void 必須項目がないと400() throws Exception {
    mockMvc
        .perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.displayName").exists())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void 壊れたJSONは400() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  // ---- ログイン ----

  @Test
  void 登録時と異なる大文字小文字でもログインできる() throws Exception {
    register("alice", "アリス", PASSWORD);

    String body =
        login("ALICE", PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value("alice"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token(body)))
        .andExpect(status().isOk());
  }

  @Test
  void パスワード誤りと存在しないIDは同じ401応答() throws Exception {
    register("alice", "アリス", PASSWORD);

    String wrongPassword =
        login("alice", "wrong-password")
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownUser =
        login("nobody", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(wrongPassword).isEqualTo(unknownUser);
    assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
        .isEqualTo("ユーザーIDまたはパスワードが正しくありません");
  }

  @Test
  void ログインの入力が空だと400() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  // ---- 認証が必要な API ----

  @Test
  void トークンなしの保護APIは401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  void 公開APIの以外のAPIはトークンなしだと401() throws Exception {
    mockMvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
  }

  @Test
  void ログアウトは有効なトークンで204() throws Exception {
    String body = register("alice", "アリス", PASSWORD).andReturn().getResponse().getContentAsString();

    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer " + token(body)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 署名が不正なトークンは401() throws Exception {
    long userId = registerAndGetId("alice");
    JwtEncoder other =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(
                    "another-secret-another-secret-0123456789".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256")));

    mockMvc
        .perform(
            get("/api/auth/me")
                .header(
                    "Authorization",
                    "Bearer " + encode(other, userId, Instant.now().plusSeconds(60))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 期限切れのトークンは401() throws Exception {
    long userId = registerAndGetId("alice");

    mockMvc
        .perform(
            get("/api/auth/me")
                .header(
                    "Authorization",
                    "Bearer " + encode(jwtEncoder, userId, Instant.now().minusSeconds(3600))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 存在しないユーザーのトークンは401() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/me")
                .header(
                    "Authorization",
                    "Bearer " + encode(jwtEncoder, 999_999L, Instant.now().plusSeconds(60))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 壊れたトークンは401() throws Exception {
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void healthは認証なしで200() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  // ---- ヘルパー ----

  static Stream<String> invalidPasswords() {
    return Stream.of("1234567", "a".repeat(73), "あ".repeat(25));
  }

  static Stream<String> validPasswords() {
    return Stream.of("12345678", "a".repeat(72), "あ".repeat(24));
  }

  static Stream<String> invalidDisplayNames() {
    return Stream.of("", "   ", "a".repeat(31));
  }

  private ResultActions register(String username, String displayName, String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(registerJson(username, displayName, password)));
  }

  private ResultActions login(String username, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
  }

  private static String registerJson(String username, String displayName, String password) {
    return "{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}"
        .formatted(username, displayName, password);
  }

  private static String token(String authResponseBody) {
    return JsonPath.read(authResponseBody, "$.accessToken");
  }

  private long registerAndGetId(String username) throws Exception {
    String body = register(username, "名前", PASSWORD).andReturn().getResponse().getContentAsString();
    return JsonPath.<Number>read(body, "$.user.id").longValue();
  }

  private static String encode(JwtEncoder encoder, long userId, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .issuedAt(expiresAt.minusSeconds(120))
            .expiresAt(expiresAt)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
