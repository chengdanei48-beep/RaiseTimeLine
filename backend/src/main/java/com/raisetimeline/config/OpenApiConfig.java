package com.raisetimeline.config;

import com.raisetimeline.dto.ProblemResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * API 仕様書(OpenAPI)の共通設定。仕様は docs/api/openapi.yaml にも生成して残す(テストで一致を確認する)。 仕様と Swagger UI は、環境変数
 * API_DOCS_ENABLED=true のときだけ公開される(application.properties)。
 */
@Configuration
public class OpenApiConfig {

  static final String BEARER = "bearerAuth";
  private static final String PROBLEM = "Problem";

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("RaiseTimeLine API")
                .version("v1")
                .description(
                    """
                    テキスト中心の SNS「RaiseTimeLine」の API。インプレッション数・リポストに当たる機能はない。

                    - 認証: `POST /api/auth/login` で得たアクセストークンを `Authorization: Bearer <token>` で送る。\
                    リフレッシュトークンは HttpOnly の Cookie(Path は `/api/auth`)で受け渡す
                    - エラー: すべて `Problem`(RFC 9457 ProblemDetail)形式
                    - 一覧のページング: `cursor`(前回応答の `nextCursor`)と `limit`(1〜50、既定 20)
                    - 画像: `POST /api/uploads/presign` で署名付き URL を得て S3 へ直接 PUT し、返ったキーを保存時に渡す"""))
        .servers(List.of(new Server().url("http://localhost:8080").description("開発(ローカル)")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("ログインで得たアクセストークン")))
        // 既定はログイン必須。認証なしの API には @SecurityRequirements(空)を付ける
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  /** エラー応答の型(Problem)を、仕様の components に登録する。 */
  @Bean
  OpenApiCustomizer problemSchema() {
    return openApi -> {
      var resolved =
          ModelConverters.getInstance()
              .resolveAsResolvedSchema(new AnnotatedType(ProblemResponse.class));
      if (resolved != null) {
        if (openApi.getComponents() == null) {
          openApi.setComponents(new Components());
        }
        resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
        openApi.getComponents().addSchemas(PROBLEM, resolved.schema);
      }
    };
  }

  /**
   * 共通のエラー応答を、全 API に自動で付ける。401 はログイン必須の API、400 は入力(パス・クエリ・本文)を 受ける API、404 はパスに ID を含む
   * API。403・409・429 などは、該当する API に @ApiResponse で明記する。
   */
  @Bean
  OperationCustomizer commonErrorResponses() {
    return (operation, handlerMethod) -> {
      boolean secured = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
      boolean hasInput =
          operation.getRequestBody() != null
              || (operation.getParameters() != null && !operation.getParameters().isEmpty());
      boolean hasPathId =
          operation.getParameters() != null
              && operation.getParameters().stream().anyMatch(p -> "path".equals(p.getIn()));
      operation.setOperationId(operationId(handlerMethod));
      describeCommonParameters(operation);
      if (hasInput) {
        addError(operation, "400", "入力内容の誤り");
      }
      if (secured) {
        addError(operation, "401", "未ログイン、またはトークンが無効・期限切れ");
      }
      if (hasPathId) {
        addError(operation, "404", "対象が存在しない(削除済みを含む)");
      }
      return operation;
    };
  }

  /** operationId は「コントローラ名_メソッド名」(例: post_create)。汎用名の重複(create・get など)を避ける。 */
  private static String operationId(HandlerMethod handlerMethod) {
    String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
    String prefix = Character.toLowerCase(controller.charAt(0)) + controller.substring(1);
    return prefix + "_" + handlerMethod.getMethod().getName();
  }

  /** 複数の API で共通のパラメータ(ページング・検索語・パスの ID)に、説明と範囲を付ける。 */
  private static void describeCommonParameters(Operation operation) {
    if (operation.getParameters() == null) {
      return;
    }
    for (Parameter p : operation.getParameters()) {
      switch (p.getName()) {
        case "cursor" -> p.setDescription("前回の応答の `nextCursor`。省略すると先頭から(中身は不透明な文字列で、解釈しない)");
        case "limit" -> {
          p.setDescription("1ページの件数。1〜50、省略時は 20");
          p.getSchema().setMinimum(BigDecimal.valueOf(1));
          p.getSchema().setMaximum(BigDecimal.valueOf(50));
          p.getSchema().setDefault(20);
        }
        case "q" -> {
          p.setDescription("検索語。前後の空白と先頭の `@` は無視し、1〜50文字(空は 400)");
          p.setRequired(true);
        }
        case "id" -> {
          if ("path".equals(p.getIn())) {
            p.setDescription("対象の ID(内部ID・数値)");
          }
        }
        case "postId" -> p.setDescription("投稿の ID");
        default -> {
          // 個別に @Parameter で説明している
        }
      }
    }
  }

  private static void addError(Operation operation, String code, String description) {
    if (operation.getResponses() != null && operation.getResponses().containsKey(code)) {
      return; // @ApiResponse で個別に書いてあればそちらを優先する
    }
    Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM);
    operation
        .getResponses()
        .addApiResponse(
            code,
            new ApiResponse()
                .description(description)
                .content(
                    new Content().addMediaType("application/json", new MediaType().schema(ref))));
  }
}
