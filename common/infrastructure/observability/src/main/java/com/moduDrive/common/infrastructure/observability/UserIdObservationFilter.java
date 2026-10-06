package com.moduDrive.common.infrastructure.observability;

import io.micrometer.common.KeyValue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.io.IOException;

/** Puts the caller's id on the request's server span ({@code user.id}) and on every log line it
 * writes ({@code userId}), so "user X says it failed around 10:00" can be found without an error
 * code. Never a metric tag — one series per user would explode Prometheus. */
class UserIdObservationFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X_USER_ID";
    static final String SPAN_TAG = "user.id";
    static final String MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }
        ServerHttpObservationFilter.findObservationContext(request)
                .ifPresent(context -> context.addHighCardinalityKeyValue(KeyValue.of(SPAN_TAG, userId)));
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, userId)) {
            chain.doFilter(request, response);
        }
    }
}
