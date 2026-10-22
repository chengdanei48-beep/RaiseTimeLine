package com.raisetimeline.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** MyBatis が発行した SQL の数を数える(N+1 の検知用)。テストで @Import して使う。 */
@TestConfiguration(proxyBeanMethods = false)
public class SqlCountConfig {

  /** 数え始めからの発行数。テストは直列で動く前提。 */
  public static final AtomicInteger COUNT = new AtomicInteger();

  @Bean
  Interceptor sqlCountInterceptor() {
    return new SqlCountInterceptor();
  }

  @Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class})
  })
  static class SqlCountInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      COUNT.incrementAndGet();
      return invocation.proceed();
    }
  }
}
