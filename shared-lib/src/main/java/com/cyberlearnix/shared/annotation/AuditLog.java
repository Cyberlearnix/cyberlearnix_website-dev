package com.cyberlearnix.shared.annotation;

import java.lang.annotation.*;

/**
 * Annotation to trigger audit logging via AspectJ
 * Applied to authentication-related methods to log events
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String action() default "";
}
