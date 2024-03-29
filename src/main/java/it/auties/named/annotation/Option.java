package it.auties.named.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface Option {
    String DEFAULT_VALUE = "<default>";

    String value() default DEFAULT_VALUE;
}
