package com.raisetimeline.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisetimeline.support.PostgresTestContainer;
import com.raisetimeline.support.TestUsers;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 構造化ログ(JSON)・request_id・アクセスログ・認証イベント・秘密情報が出ないこと。実際の出力(標準出力)を取り込んで確かめる。 */
@ExtendWith(OutputCaptureExtension.class)
@ImportTestcontainers(PostgresTestContainer.class)
@SpringBootTest(
    properties = {
      "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
      "logging.structured.format.console=ecs"
    })
@AutoConfigureMockMvc
class StructuredLoggingTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String PASSWORD = "password123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM users");
  }

  /** 出力のうち JSON として読める行だけを取り出す。 */
  private static List<JsonNode> jsonLines(CapturedOutput output) {
    return Stream.of(output.getAll().split("\\R"))
        .filter(line -> line.startsWith("{"))
        .map(
            line -> {
              try {
                return JSON.readTree(line);
              } catch (Exception e) {
                throw new AssertionError("JSON として読めないログ行: " + line, e);
              }
            })
        .toList();
  }

  /** "http.status" のような名前を、平らなキー・入れ子のどちらの出力でも読む。 */
  private static JsonNode field(JsonNode node, String name) {
    if (node.has(name)) {
      return node.get(name);
    }
    JsonNode current = node;
    for (String part : name.split("\\.")) {
      current = current == null ? null : current.get(part);
    }
    return current;
  }

  private static JsonNode accessLog(CapturedOutput output, String route) {
    return jsonLines(output).stream()
        .filter(n -> "http_request".equals(text(n, "event")))
        .filter(n -> route.equals(text(n, "http.route")))
        .reduce((first, second) -> second)
        .orElseThrow(() -> new AssertionError("アクセスログがありません: " + route));
  }

  private static String text(JsonNode node, String name) {
    JsonNode value = field(node, name);
    return value == null || value.isNull() ? null : value.asText();
  }

  @Test
  void ログはJSON形式で出力される(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

    List<JsonNode> lines = jsonLines(output);
    assertThat(lines).isNotEmpty();
    JsonNode line = lines.get(lines.size() - 1);
    assertThat(line.get("@timestamp")).isNotNull();
    assertThat(text(line, "log.level")).isNotNull();
    assertThat(text(line, "message")).isNotNull();
    assertThat(text(line, "service.name")).isEqualTo("raisetimeline");
  }

  @Test
  void アクセスログにメソッド_ルート_ステータス_所要時間が載りIDはテンプレートで記録される(CapturedOutput output) throws Exception {
    String token = TestUsers.registerAndGetToken(mockMvc, "loguser", "ログ");

    mockMvc
        .perform(get("/api/posts/987654").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());

    JsonNode log = accessLog(output, "/api/posts/{id}");
    assertThat(text(log, "http.method")).isEqualTo("GET");
    assertThat(text(log, "http.status")).isEqualTo("404");
    assertThat(Long.parseLong(text(log, "duration_ms"))).isNotNegative();
    assertThat(log.toString()).doesNotContain("987654");
  }

  @Test
  void 全てのログにrequest_idが付き応答ヘッダーと一致する(CapturedOutput output) throws Exception {
    String requestId =
        mockMvc
            .perform(get("/api/me"))
            .andReturn()
            .getResponse()
            .getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);

    assertThat(requestId).isNotBlank();
    JsonNode log =
        jsonLines(output).stream()
            .filter(n -> requestId.equals(text(n, "request_id")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("request_id のログがありません"));
    assertThat(text(log, "event")).isEqualTo("http_request");
  }

  @Test
  void 安全な形式のX_Request_Idはそのまま採用される() throws Exception {
    String echoed =
        mockMvc
            .perform(get("/api/me").header("X-Request-Id", "client-req_123.abc"))
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");

    assertThat(echoed).isEqualTo("client-req_123.abc");
  }

  @Test
  void 危険な形式のX_Request_Idは捨てて新しく採番する(CapturedOutput output) throws Exception {
    String malicious = "x\",\"level\":\"FATAL";

    String issued =
        mockMvc
            .perform(get("/api/me").header("X-Request-Id", malicious))
            .andReturn()
            .getResponse()
            .getHeader("X-Request-Id");

    assertThat(issued).isNotEqualTo(malicious).matches("[0-9a-f-]{36}");
    assertThat(output.getAll()).doesNotContain("FATAL");
  }

  @Test
  void 認証済みのリクエストはuser_idが付く(CapturedOutput output) throws Exception {
    String body = TestUsers.register(mockMvc, "uidlog", "ID");
    String token = TestUsers.accessToken(body);
    long userId = TestUsers.userId(body);

    mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));

    assertThat(text(accessLog(output, "/api/auth/me"), "user_id"))
        .isEqualTo(String.valueOf(userId));
  }

  @Test
  void 未認証のリクエストにはuser_idが付かない(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/api/me"));

    JsonNode log =
        jsonLines(output).stream()
            .filter(n -> "http_request".equals(text(n, "event")))
            .reduce((a, b) -> b)
            .orElseThrow();
    assertThat(text(log, "user_id")).isNull();
  }

  @Test
  void ルートが決まらない401は生のパスをurl_pathに記録する(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/api/posts/42"));

    JsonNode log =
        jsonLines(output).stream()
            .filter(n -> "http_request".equals(text(n, "event")))
            .reduce((a, b) -> b)
            .orElseThrow();
    assertThat(text(log, "http.status")).isEqualTo("401");
    assertThat(text(log, "url.path")).isEqualTo("/api/posts/42");
    assertThat(text(log, "http.route")).isNull();
  }

  @Test
  void ヘルスチェックはアクセスログに出さない(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());

    assertThat(jsonLines(output))
        .noneMatch(n -> "/api/health".equals(text(n, "url.path")))
        .noneMatch(n -> text(n, "message") != null && text(n, "message").contains("/api/health"));
  }

  @Test
  void ログイン成功と失敗がイベントとして記録され_失敗には入力値が含まれない(CapturedOutput output) throws Exception {
    TestUsers.register(mockMvc, "evtuser", "イベント");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"evtuser\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghostuser\",\"password\":\"wrong-pass-999\"}"))
        .andExpect(status().isUnauthorized());

    List<JsonNode> lines = jsonLines(output);
    assertThat(lines).anyMatch(n -> "login_succeeded".equals(text(n, "event")));
    JsonNode failed =
        lines.stream()
            .filter(n -> "login_failed".equals(text(n, "event")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("login_failed がありません"));
    assertThat(text(failed, "log.level")).isEqualTo("WARN");
    assertThat(failed.toString()).doesNotContain("ghostuser").doesNotContain("wrong-pass-999");
  }

  @Test
  void ユーザー登録がイベントとして記録される(CapturedOutput output) throws Exception {
    String body = TestUsers.register(mockMvc, "regevt", "登録");

    JsonNode event =
        jsonLines(output).stream()
            .filter(n -> "user_registered".equals(text(n, "event")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("user_registered がありません"));
    assertThat(text(event, "user_id")).isEqualTo(String.valueOf(TestUsers.userId(body)));
  }

  @Test
  void パスワードとトークンはログに出ない(CapturedOutput output) throws Exception {
    String secretPassword = "Sup3r-Secret-Pass-777";
    var register =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"secretuser\",\"displayName\":\"秘密\",\"password\":\""
                            + secretPassword
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    String accessToken = TestUsers.accessToken(register.getContentAsString());
    String refreshToken = register.getCookie("refresh_token").getValue();
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"secretuser\",\"password\":\"" + secretPassword + "\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken));

    assertThat(output.getAll())
        .doesNotContain(secretPassword)
        .doesNotContain(accessToken)
        .doesNotContain(refreshToken)
        .doesNotContain("test-secret-test-secret");
  }
}
