package com.raisetimeline.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** UTF-8 でエンコードしたときのバイト数の上限(BCrypt は 72 バイトを超える部分を無視するため)。 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

  int value();

  String message() default "文字列が長すぎます";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
