package com.moduDrive.common.infrastructure.observability;

import io.micrometer.common.KeyValue;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdObservationFilterTest {

    private final UserIdObservationFilter filter = new UserIdObservationFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/files");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
    private final AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(UserIdObservationFilter.MDC_KEY));

    {
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, context);
    }

    @Nested
    @DisplayName("게이트웨이가 사용자 id 헤더를 넘겼을 때")
    class WhenUserIdHeaderIsPresent {

        @Test
        void tagsServerSpanAndLogsOnlyDuringRequest() throws Exception {
            // given
            request.addHeader(UserIdObservationFilter.USER_ID_HEADER, "user-1");

            // when
            filter.doFilter(request, response, chain);

            // then
            assertThat(context.getHighCardinalityKeyValues())
                    .contains(KeyValue.of(UserIdObservationFilter.SPAN_TAG, "user-1"));
            assertThat(mdcDuringChain.get()).isEqualTo("user-1");
            assertThat(MDC.get(UserIdObservationFilter.MDC_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("헤더가 없을 때 (로그인 전 요청, 내부 호출)")
    class WhenUserIdHeaderIsMissing {

        @Test
        void leavesSpanAndLogsUntouched() throws Exception {
            // when
            filter.doFilter(request, response, chain);

            // then
            assertThat(context.getHighCardinalityKeyValues()).isEmpty();
            assertThat(mdcDuringChain.get()).isNull();
        }
    }
}
