package com.moduDrive.common.core.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/** Class-level stereotype for a class whose methods react to events: SQS consumers
 * ({@code adapter/in/messaging}) and in-process Spring event listeners ({@code application/event}).
 * Not Spring's method-level {@link org.springframework.context.event.EventListener}; the methods
 * still carry {@code @SqsListener} or {@code @TransactionalEventListener}. */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface EventListener {

    @AliasFor(annotation = Component.class)
    String value() default "";

}