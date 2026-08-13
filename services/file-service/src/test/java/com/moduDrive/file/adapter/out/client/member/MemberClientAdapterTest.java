package com.moduDrive.file.adapter.out.client.member;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.exception.FileExceptionCase;
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
        return Request.create(Request.HttpMethod.GET, "/api/v1/member/find-by-email",
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

            UUID result = memberClientAdapter.findMemberIdByEmail(EMAIL);

            assertThat(result).isEqualTo(memberId);
        }
    }

    @Nested
    @DisplayName("member-service가 4xx로 회원 없음을 알릴 때")
    class WhenMemberServiceRejectsWithClientError {

        @Test
        void translatesToShareTargetNotFound() {
            willThrow(new FeignException.BadRequest("member not found", request(), null, Map.of()))
                    .given(memberClient).findMemberByEmail(EMAIL);

            Throwable thrown = catchThrowable(() -> memberClientAdapter.findMemberIdByEmail(EMAIL));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.SHARE_TARGET_NOT_FOUND);
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
}
