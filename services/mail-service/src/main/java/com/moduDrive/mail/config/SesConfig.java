package com.moduDrive.mail.config;

import io.awspring.cloud.autoconfigure.ses.SesClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class SesConfig {

    /** Caps one send so it can't hold a listener thread for minutes — the SDK sets no limit of its own,
     * only a 30s socket read timeout per attempt. Kept inside the mail queues'
     * visibility timeout (10s) so the message isn't redelivered while the send is still waiting. A send
     * that hits the cap may still go out; the listener treats it as unknown, not failed. SDK retries are off:
     * SendRawEmail isn't idempotent, so a resend after a lost response would mail twice — the SQS
     * redelivery, guarded by the Send event, does the retrying instead. */
    @Bean
    SesClientCustomizer sesTimeouts() {
        return builder -> builder.overrideConfiguration(o -> o
                .apiCallTimeout(Duration.ofSeconds(7))
                .retryStrategy(r -> r.maxAttempts(1)));
    }
}
