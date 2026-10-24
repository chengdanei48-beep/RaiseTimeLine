package com.raisetimeline.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** テスト用のユーザー登録(パスワードは全員 password123)。 */
public final class TestUsers {

  private TestUsers() {}

  /** 登録して、応答の JSON を返す(accessToken と user を含む)。 */
  public static String register(MockMvc mockMvc, String username, String displayName)
      throws Exception {
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

  /** 表示名をユーザーIDと同じにして登録する。 */
  public static String register(MockMvc mockMvc, String username) throws Exception {
    return register(mockMvc, username, username);
  }

  /** 登録して、アクセストークンだけを返す。 */
  public static String registerAndGetToken(MockMvc mockMvc, String username, String displayName)
      throws Exception {
    return accessToken(register(mockMvc, username, displayName));
  }

  public static String accessToken(String registerResponse) {
    return JsonPath.read(registerResponse, "$.accessToken");
  }

  public static long userId(String registerResponse) {
    return JsonPath.<Number>read(registerResponse, "$.user.id").longValue();
  }
}
