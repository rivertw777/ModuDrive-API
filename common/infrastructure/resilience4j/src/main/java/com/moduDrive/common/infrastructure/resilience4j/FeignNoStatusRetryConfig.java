package com.moduDrive.common.infrastructure.resilience4j;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.clientconfig.HttpClient5FeignConfiguration.HttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feign's Apache client would on its own resend a 503 or 429 after its {@code Retry-After} (10s),
 * holding the caller's request that long behind Resilience4j's back. Retries are Resilience4j's call
 * (spec 006 1-2): a 503 goes back to the caller as is. IO-error retries (a stale pooled connection)
 * stay as Apache does them. */
@Configuration
@ConditionalOnClass(HttpClientBuilderCustomizer.class)
public class FeignNoStatusRetryConfig {

    @Bean
    HttpClientBuilderCustomizer noStatusRetry() {
        return builder -> builder.setRetryStrategy(new DefaultHttpRequestRetryStrategy() {
            @Override
            public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
                return false;
            }
        });
    }
}
