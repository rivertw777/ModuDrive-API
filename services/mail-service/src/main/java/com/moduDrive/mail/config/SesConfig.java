package com.moduDrive.mail.config;

import io.awspring.cloud.autoconfigure.ses.SesClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class SesConfig {

    /** The SDK has no per-call limit (only a 30s socket read timeout), so cap a send inside the mail
     * queues' 10s visibility timeout. A send that hits the cap may still go out; the listener treats it
     * as unknown. SDK retries are off because SendRawEmail isn't idempotent; SQS redelivery, guarded by
     * the Send event, does the retrying. Added to the configuration already there: the Consumer overload
     * starts from scratch and would drop the tracing interceptor applied before it. */
    @Bean
    SesClientCustomizer sesTimeouts() {
        return builder -> builder.overrideConfiguration(builder.overrideConfiguration().toBuilder()
                .apiCallTimeout(Duration.ofSeconds(7))
                .retryStrategy(r -> r.maxAttempts(1))
                .build());
    }
}
