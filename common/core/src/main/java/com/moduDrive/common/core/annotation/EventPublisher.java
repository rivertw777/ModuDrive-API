package com.moduDrive.common.core.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/** Class-level stereotype for an out adapter that publishes events to other services
 * ({@code adapter/out/messaging}, via the outbox). */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EventPublisher {

    @AliasFor(annotation = Component.class)
    String value() default "";

}