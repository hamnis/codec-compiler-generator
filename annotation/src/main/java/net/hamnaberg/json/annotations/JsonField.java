package net.hamnaberg.json.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JsonField {
    String value();
}
