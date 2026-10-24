package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MemberClientAdapterTest {

    @Mock private MemberClient memberClient;
    @InjectMocks private MemberClientAdapter memberClientAdapter;

    private static final String EMAIL = "river@modudrive.com";

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/internal/v1/member/by-email",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    @Nested
    @DisplayName("member-service가 회원을 반환할 때")
    class WhenMemberExists {

        @Test
        void returnsMemberId() {
            UUID memberId = UUID.randomUUID();
            given(memberClient.findMemberByEmail(EMAIL))
                    .willReturn(ApiResponse.success(new MemberResponse(memberId.toString(), "river", EMAIL)));

            Optional<UUID> result = memberClientAdapter.findMemberIdByEmail(EMAIL);

            assertThat(result).contains(memberId);
        }
    }

    @Nested
    @DisplayName("member-service가 4xx로 회원 없음을 알릴 때")
    class WhenMemberServiceRejectsWithClientError {

        @Test
        void resolvesToEmpty() {
            willThrow(new FeignException.BadRequest("member not found", request(), null, Map.of()))
                    .given(memberClient).findMemberByEmail(EMAIL);

            Optional<UUID> result = memberClientAdapter.findMemberIdByEmail(EMAIL);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("member-service가 5xx로 실패할 때")
    class WhenMemberServiceFails {

        @Test
        void rethrowsSoTheFailureIsNotMistakenForAMissingMember() {
            FeignException serverError =
                    new FeignException.InternalServerError("boom", request(), null, Map.of());
            willThrow(serverError).given(memberClient).findMemberByEmail(EMAIL);

            Throwable thrown = catchThrowable(() -> memberClientAdapter.findMemberIdByEmail(EMAIL));

            assertThat(thrown).isSameAs(serverError);
        }
    }

    @Nested
    @DisplayName("id로 회원을 조회할 때")
    class WhenFindingMemberById {

        @Test
        void returnsMemberSummary() {
            UUID memberId = UUID.randomUUID();
            given(memberClient.findMemberById(memberId.toString()))
                    .willReturn(ApiResponse.success(new MemberResponse(memberId.toString(), "river", EMAIL)));

            var result = memberClientAdapter.findMemberByIdOrUnknown(memberId);

            assertThat(result.name()).isEqualTo("river");
            assertThat(result.email()).isEqualTo(EMAIL);
        }

        @Test
        void degradesToUnknownWhenLookupFails() {
            UUID memberId = UUID.randomUUID();
            willThrow(new FeignException.NotFound("member not found", request(), null, Map.of()))
                    .given(memberClient).findMemberById(memberId.toString());

            var result = memberClientAdapter.findMemberByIdOrUnknown(memberId);

            assertThat(result).isEqualTo(FindMemberByIdPort.UNKNOWN_MEMBER);
        }

        @Test
        void degradesToUnknownWhenMemberServiceIsDown() {
            UUID memberId = UUID.randomUUID();
            willThrow(new BusinessException(CircuitBreakerExceptionCase.SERVICE_IS_OPEN))
                    .given(memberClient).findMemberById(memberId.toString());

            var result = memberClientAdapter.findMemberByIdOrUnknown(memberId);

            assertThat(result).isEqualTo(FindMemberByIdPort.UNKNOWN_MEMBER);
        }

        @Test
        void degradesToUnknownWhenResponseHasNoData() {
            UUID memberId = UUID.randomUUID();
            given(memberClient.findMemberById(memberId.toString())).willReturn(ApiResponse.success(null));

            var result = memberClientAdapter.findMemberByIdOrUnknown(memberId);

            assertThat(result).isEqualTo(FindMemberByIdPort.UNKNOWN_MEMBER);
        }
    }
}
