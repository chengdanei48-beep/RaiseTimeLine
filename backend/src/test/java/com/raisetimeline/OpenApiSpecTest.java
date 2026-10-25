package com.raisetimeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.support.FakeStorageConfig;
import com.raisetimeline.support.PostgresTestContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 仕様書(docs/api/openapi.yaml)と、実際のコントローラから生成した仕様が一致することを確かめる。 API
 * を変えたのに仕様書を更新し忘れると、このテストが失敗する(CI でも検知される)。
 *
 * <p>仕様書を更新するときは: {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(
    properties = {
      "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
@AutoConfigureMockMvc
@ImportTestcontainers(PostgresTestContainer.class)
@Import(FakeStorageConfig.class)
class OpenApiSpecTest {

  /** テストは backend/ で動くので、リポジトリ直下の docs/ は一つ上。 */
  private static final Path SPEC_FILE = Path.of("..", "docs", "api", "openapi.yaml");

  @Autowired private MockMvc mockMvc;

  @Test
  void 仕様書は実際のAPIと一致している() throws Exception {
    String generated = normalize(fetch("/v3/api-docs.yaml"));

    if (Boolean.getBoolean("openapi.update")) {
      Files.createDirectories(SPEC_FILE.getParent());
      Files.writeString(SPEC_FILE, generated, StandardCharsets.UTF_8);
      return;
    }

    assertThat(SPEC_FILE)
        .as("仕様書がありません。./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true で生成する")
        .exists();
    String committed = normalize(Files.readString(SPEC_FILE, StandardCharsets.UTF_8));
    assertThat(generated)
        .as(
            "docs/api/openapi.yaml が実際の API と違います。"
                + "./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true で更新してください")
        .isEqualTo(committed);
  }

  @Test
  void 全てのAPIに要約と説明がある() throws Exception {
    String json = fetch("/v3/api-docs");
    var spec = com.jayway.jsonpath.JsonPath.parse(json);
    java.util.Map<String, java.util.Map<String, java.util.Map<String, Object>>> paths =
        spec.read("$.paths");
    paths.forEach(
        (path, methods) ->
            methods.forEach(
                (method, operation) -> {
                  assertThat(operation.get("summary"))
                      .as("%s %s に @Operation(summary) がありません", method.toUpperCase(), path)
                      .isNotNull();
                  assertThat(operation.get("tags"))
                      .as("%s %s に @Tag がありません", method.toUpperCase(), path)
                      .isNotNull();
                }));
  }

  @Test
  void 認証なしのAPIは認証不要と明記されている() throws Exception {
    var spec = com.jayway.jsonpath.JsonPath.parse(fetch("/v3/api-docs"));
    for (String path :
        new String[] {
          "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
        }) {
      java.util.List<Object> security = spec.read("$.paths['" + path + "'].post.security");
      assertThat(security).as("%s は認証なし", path).isEmpty();
    }
    // それ以外は、全体の既定(Bearer 認証)
    assertThat(spec.<java.util.List<Object>>read("$.security")).hasSize(1);
  }

  private String fetch(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /** 改行コード(Windows の CRLF)と末尾の空白の違いを、差分にしない。 */
  private static String normalize(String text) {
    return text.replace("\r\n", "\n").stripTrailing() + "\n";
  }
}
