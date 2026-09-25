package com.moduDrive.member.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.exception.MemberExceptionCase;
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
 * Gate on {@code /internal/**} — service-to-service only. Left unauthenticated, it exposes a
 * rate-limit-free credential check ({@code AuthenticateMemberController}, reachable outside the
 * gateway) to anything on the internal network (issue #332, file-service's counterpart is #314). Only the gateway not
 * proxying this prefix kept an arbitrary caller out before; that is a deployment accident, not an
 * authorization check.
 *
 * <p>A caller without the shared secret gets the same {@code MEMBER_NOT_FOUND} body these routes
 * already use for a genuine miss, so the response never confirms an internal route exists at all.
 *
 * <p>Registered — and scoped to {@code /internal/*} — by {@link InternalTokenFilterConfig}; it is
 * deliberately not a {@code @Component}, so it stays out of the web slice tests of the
 * tenant-facing controllers that never carry this header.
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
            respondNotFound(response);
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

    private void respondNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(MemberExceptionCase.MEMBER_NOT_FOUND.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(MemberExceptionCase.MEMBER_NOT_FOUND));
    }
}
