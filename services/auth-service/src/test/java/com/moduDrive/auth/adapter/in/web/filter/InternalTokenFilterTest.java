package com.moduDrive.auth.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.auth.exception.AuthExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenFilterTest {

    private static final String TOKEN = "s3cr3t-internal-token";

    private final InternalTokenFilter internalTokenFilter =
            new InternalTokenFilter(TOKEN, new ObjectMapper());

    private MockHttpServletRequest internalRequest(String presentedToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/sessions/validate");
        if (presentedToken != null) {
            request.addHeader(InternalTokenFilter.HEADER, presentedToken);
        }
        return request;
    }

    @Nested
    @DisplayName("설정값과 같은 토큰을 제시했을 때")
    class WhenTokenMatches {

        @Test
        void passesTheRequestDownTheChain() throws Exception {
            MockHttpServletRequest request = internalRequest(TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            internalTokenFilter.doFilter(request, response, filterChain);

            assertThat(filterChain.getRequest()).isSameAs(request);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("토큰이 틀리거나 아예 없을 때")
    class WhenTokenIsWrongOrMissing {

        @Test
        @DisplayName("체인을 태우지 않고 세션 없음과 같은 응답으로 답한다")
        void answersSessionNotFoundWithoutReachingTheController() throws Exception {
            for (String presented : new String[]{null, "", "wrong-token", TOKEN + "x"}) {
                MockHttpServletRequest request = internalRequest(presented);
                MockHttpServletResponse response = new MockHttpServletResponse();
                MockFilterChain filterChain = new MockFilterChain();

                internalTokenFilter.doFilter(request, response, filterChain);

                assertThat(filterChain.getRequest())
                        .as("token '%s' should never reach the controller", presented)
                        .isNull();
                assertThat(response.getStatus()).isEqualTo(AuthExceptionCase.SESSION_NOT_FOUND.getHttpStatus().value());
                assertThat(response.getContentAsString())
                        .contains(AuthExceptionCase.SESSION_NOT_FOUND.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("시크릿이 설정되지 않았을 때")
    class WhenTokenIsNotConfigured {

        @Test
        @DisplayName("빈 값으로는 기동조차 하지 않는다")
        void refusesToStart() {
            for (String unset : new String[]{null, "", "   "}) {
                assertThatThrownBy(() -> new InternalTokenFilter(unset, new ObjectMapper()))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }
}
