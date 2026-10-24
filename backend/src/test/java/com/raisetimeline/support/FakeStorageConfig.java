package com.raisetimeline.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** S3 の代わりに {@link FakeObjectStorage} を使う。テストクラスに {@code @Import} して使う。 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeStorageConfig {

  @Bean
  @Primary
  FakeObjectStorage fakeObjectStorage() {
    return new FakeObjectStorage();
  }
}
