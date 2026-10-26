package com.raisetimeline.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 認証済みのリクエストのログに user_id を付ける。 セキュリティのフィルタチェーンで、Bearer トークンの認証の直後に置く(認証前はユーザーが分からない)。 MDC の後始末は
 * {@link RequestLoggingFilter} が行う(アクセスログにも user_id を載せるため、ここでは消さない)。
 *
 * <p>Bean にはしない(Bean にすると、セキュリティの外でも自動登録されて二重に動く)。
 */
public class UserMdcFilter extends OncePerRequestFilter {

  public static final String USER_ID_KEY = "user_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
      MDC.put(USER_ID_KEY, auth.getName());
    }
    chain.doFilter(request, response);
  }
}
