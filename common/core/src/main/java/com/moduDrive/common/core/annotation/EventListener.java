package com.moduDrive.common.core.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/** Class-level stereotype for an inbound adapter that consumes events from other services
 * ({@code adapter/in/messaging}). Not Spring's method-level
 * {@link org.springframework.context.event.EventListener}; the methods still carry {@code @SqsListener}. */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EventListener {

    @AliasFor(annotation = Component.class)
    String value() default "";

}