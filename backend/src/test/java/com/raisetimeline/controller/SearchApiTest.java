package com.raisetimeline.controller;

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

@SpringBootTest(properties = "app.jwt.secret=test-secret-test-secret-test-secret-0123456789")
@AutoConfigureMockMvc
@ImportTestcontainers(PostgresTestContainer.class)
class SearchApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String me;
  private long hanakoId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM users");
    me = TestUsers.registerAndGetToken(mockMvc, "me_user", "わたし");
    hanakoId = TestUsers.userId(TestUsers.register(mockMvc, "hanako", "花子"));
    TestUsers.register(mockMvc, "Hanayama", "花山太郎");
    TestUsers.register(mockMvc, "jiro", "次郎");
    TestUsers.register(mockMvc, "a_b", "アンダー");
    TestUsers.register(mockMvc, "abx", "エービー");
  }

  @Test
  void ユーザーIDの部分一致で大文字小文字を区別せず見つかる() throws Exception {
    search("hana")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].username").value("hanako"))
        .andExpect(jsonPath("$.items[1].username").value("Hanayama"))
        .andExpect(jsonPath("$.items[0].avatarUrl").doesNotExist());
    search("HANA").andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void 表示名でも見つかる() throws Exception {
    search("花子")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].username").value("hanako"));
  }

  @Test
  void 先頭の_は無視され前後の空白は除かれる() throws Exception {
    search("@hana").andExpect(jsonPath("$.items.length()").value(2));
    search("  @hana  ").andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void 自分自身は結果に出ない() throws Exception {
    search("me_user").andExpect(jsonPath("$.items.length()").value(0));
    search("わたし").andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void パーセントやアンダースコアは文字として検索される() throws Exception {
    // % だけでは全員に一致しない(どのユーザーIDにも表示名にも % はない)
    search("%").andExpect(jsonPath("$.items.length()").value(0));
    // _ は「任意の1文字」ではなく文字として扱う。a_b だけが一致し、abx は一致しない
    search("_")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].username").value("a_b"));
    search("\\").andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void 該当なしは200で空() throws Exception {
    search("zzzz")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void 投稿本文はヒットしない() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", bearer(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"uniquewordinpost\"}"))
        .andExpect(status().isCreated());
    search("uniquewordinpost").andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void フォロー済みかどうかが結果に付く() throws Exception {
    mockMvc
        .perform(put("/api/users/" + hanakoId + "/follow").header("Authorization", bearer(me)))
        .andExpect(status().isNoContent());

    search("hana")
        .andExpect(jsonPath("$.items[0].username").value("hanako"))
        .andExpect(jsonPath("$.items[0].followedByMe").value(true))
        .andExpect(jsonPath("$.items[1].followedByMe").value(false));
  }

  @Test
  void カーソルでページングできる() throws Exception {
    String page1 =
        search("hana", "&limit=1")
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].username").value("hanako"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(page1, "$.nextCursor");

    search("hana", "&limit=1&cursor=" + cursor)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].username").value("Hanayama"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void 検索語が空か長すぎるか不正なときは400() throws Exception {
    mockMvc
        .perform(get("/api/users").header("Authorization", bearer(me)))
        .andExpect(status().isBadRequest());
    search("").andExpect(status().isBadRequest());
    search("   ").andExpect(status().isBadRequest());
    search("@").andExpect(status().isBadRequest());
    search("a".repeat(51)).andExpect(status().isBadRequest());
    search("a".repeat(50)).andExpect(status().isOk());
    search("hana", "&cursor=xxx").andExpect(status().isBadRequest());
    search("hana", "&limit=0").andExpect(status().isBadRequest());
  }

  @Test
  void 未認証では検索できない() throws Exception {
    mockMvc.perform(get("/api/users?q=hana")).andExpect(status().isUnauthorized());
  }

  // ---- ヘルパー ----

  private ResultActions search(String q) throws Exception {
    return search(q, "");
  }

  /** extra は、続けて付ける検索条件(例: "&limit=1&cursor=…")。q は URI テンプレートで符号化される。 */
  private ResultActions search(String q, String extra) throws Exception {
    return mockMvc.perform(get("/api/users?q={q}" + extra, q).header("Authorization", bearer(me)));
  }

  /** 登録して、応答の JSON を返す。 */
  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
