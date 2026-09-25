package com.moduDrive.auth.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gate on {@code /internal/**} — the gateway's session check. Without it, anything on the internal
 * network could probe which session ids are live. A caller without the shared secret gets the same
 * {@code SESSION_NOT_FOUND} a dead session gets, so the response never confirms the route exists.
 *
 * <p>Registered — and scoped to {@code /internal/*} — by {@link InternalTokenFilterConfig}; it is
 * deliberately not a {@code @Component}, so it stays out of the web slice tests of the
 * browser-facing controllers that never carry this header.
 */
class InternalTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    InternalTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        if (expectedToken == null || expectedToken.isBlank()) {
            // Fail at startup rather than boot a service whose internal routes accept an empty
            // header as the password.
            throw new IllegalStateException("internal.service.token must be configured");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!matchesExpectedToken(request.getHeader(HEADER))) {
            respondSessionNotFound(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Constant-time comparison: a byte-by-byte {@code equals} would let a caller recover the
     * secret one character at a time from the response timing. */
    private boolean matchesExpectedToken(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8));
    }

    private void respondSessionNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(AuthExceptionCase.SESSION_NOT_FOUND.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(AuthExceptionCase.SESSION_NOT_FOUND));
    }
}
