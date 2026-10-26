package com.raisetimeline.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 全リクエストに相関 ID(request_id)を付け、リクエストごとに 1 行のアクセスログを出す。
 *
 * <p>request_id は MDC に入れるため、そのリクエスト中の全てのログに付く。応答ヘッダー {@code X-Request-Id} でも返す。 クライアントが {@code
 * X-Request-Id} を送ってきた場合は、安全な形式のときだけ採用する(ログ汚染の防止)。 認証済みなら {@link UserMdcFilter} が user_id を足す。
 *
 * <p>パスは、ID を含む生のパスではなくテンプレート(例: {@code /api/posts/{id}})で記録する。種類が増えず、集計しやすい。 テンプレートが決まらない
 * リクエスト(未認証で弾かれたものなど)だけ、生のパスを {@code url.path} に記録する。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_KEY = "request_id";

  private static final Logger LOG = LoggerFactory.getLogger("access");
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final String HEALTH_PATH = "/api/health";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    MDC.put(REQUEST_ID_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // ヘルスチェックは頻繁で価値が低いため、アクセスログから除く
      if (!HEALTH_PATH.equals(request.getRequestURI())) {
        logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
      }
      MDC.clear();
    }
  }

  static String resolveRequestId(String incoming) {
    if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
      return incoming;
    }
    return UUID.randomUUID().toString();
  }

  private static void logAccess(
      HttpServletRequest request, HttpServletResponse response, long durationMs) {
    int status = response.getStatus();
    String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    // 5xx だけ ERROR(アラートの対象)。4xx は利用者の入力ミスなので INFO
    Level level = status >= 500 ? Level.ERROR : Level.INFO;
    var event =
        LOG.atLevel(level)
            .addKeyValue("event", "http_request")
            .addKeyValue("http.method", request.getMethod())
            .addKeyValue("http.status", status)
            .addKeyValue("duration_ms", durationMs);
    if (route != null) {
      event = event.addKeyValue("http.route", route);
    } else {
      event = event.addKeyValue("url.path", request.getRequestURI());
    }
    event.log(
        "{} {} {} {}ms",
        request.getMethod(),
        route != null ? route : request.getRequestURI(),
        status,
        durationMs);
  }
}
