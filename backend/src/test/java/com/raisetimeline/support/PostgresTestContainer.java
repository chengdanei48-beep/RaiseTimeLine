package com.raisetimeline.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * テスト用の PostgreSQL コンテナ。開発用 DB(raisetimeline-postgres)には接続しない。
 *
 * <p>テストクラスに {@code @ImportTestcontainers(PostgresTestContainer.class)} を付けて使う。 コンテナは static で 1
 * つだけ作り、全テスト(全コンテキスト)で共有する。
 */
public interface PostgresTestContainer {

  /** 開発用 DB(docker-compose.yml)と同じバージョン。 */
  @ServiceConnection PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
}
