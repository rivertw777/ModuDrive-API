package com.moduDrive.auth.adapter.out.client.member;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.api.dto.member.AuthenticateMemberResponse;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberClientAdapterTest {

    @Mock
    private MemberClient memberClient;
    @InjectMocks
    private MemberClientAdapter memberClientAdapter;

    private final AuthenticateMemberRequest request =
            new AuthenticateMemberRequest("river@modudrive.com", "raw-password");

    @Nested
    @DisplayName("유효한 회원일 때")
    class WhenMemberIsValid {

        @Test
        void returnsMemberAuthData() {
            AuthenticateMemberResponse response = new AuthenticateMemberResponse(
                    "member-id", "river", "river@modudrive.com", true, List.of("MEMBER"));
            given(memberClient.authenticateMember(request)).willReturn(ApiResponse.success(response));

            MemberAuthData result = memberClientAdapter.authenticateMember(request);

            assertThat(result.getMemberId()).isEqualTo("member-id");
            assertThat(result.getMemberRoles()).containsExactly("MEMBER");
        }
    }

    @Nested
    @DisplayName("유효하지 않은 회원일 때")
    class WhenMemberIsNotValid {

        @Test
        void throwsBusinessException() {
            AuthenticateMemberResponse response = new AuthenticateMemberResponse(
                    "member-id", "river", "river@modudrive.com", false, List.of("MEMBER"));
            given(memberClient.authenticateMember(request)).willReturn(ApiResponse.success(response));

            Throwable thrown = catchThrowable(() -> memberClientAdapter.authenticateMember(request));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.MEMBER_NOT_VALID);
        }
    }
}
