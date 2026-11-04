package org.jetbrains.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE, ElementType.FIELD})
public @interface Nullable {}
