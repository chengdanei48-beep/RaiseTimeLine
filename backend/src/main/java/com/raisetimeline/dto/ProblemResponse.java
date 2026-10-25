package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** エラー応答の形(RFC 9457 の ProblemDetail)。API 仕様書に載せるための型で、実際の応答は GlobalExceptionHandler が組み立てる。 */
@Schema(name = "Problem", description = "エラー応答(RFC 9457 ProblemDetail)")
public record ProblemResponse(
    @Schema(description = "問題の種類(URI)。通常は省略される", example = "about:blank") String type,
    @Schema(description = "ステータスの名前", example = "Bad Request") String title,
    @Schema(description = "HTTP ステータス", example = "400") int status,
    @Schema(description = "利用者に見せてよいメッセージ", example = "入力内容に誤りがあります") String detail,
    @Schema(description = "リクエストのパス", example = "/api/posts") String instance,
    @Schema(description = "項目ごとのエラー(入力検証の失敗時だけ)。キーは項目名、値はメッセージ") Map<String, String> errors) {

  public ProblemResponse {
    errors = errors == null ? Map.of() : Map.copyOf(errors);
  }
}
