package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.RequestEmailVerificationCommand;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RequestEmailVerificationServiceTest {

    @Mock
    private CheckEmailExistsPort checkEmailExistsPort;
    @Mock
    private EmailVerificationTokenPort emailVerificationTokenPort;
    @Mock
    private PublishMailEventPort publishMailEventPort;
    @InjectMocks
    private RequestEmailVerificationService requestEmailVerificationService;

    @Captor
    private ArgumentCaptor<String> savedCodeCaptor;
    @Captor
    private ArgumentCaptor<String> publishedCodeCaptor;

    private final RequestEmailVerificationCommand command =
            new RequestEmailVerificationCommand(new MemberEmail("river@modudrive.com"));

    @Nested
    @DisplayName("이메일이 중복되지 않았을 때")
    class WhenEmailIsUnique {

        @Test
        void savesSixDigitCodeAndPublishesTheSameCode() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);

            requestEmailVerificationService.requestEmailVerification(command);

            then(emailVerificationTokenPort).should()
                    .saveCode(eq("river@modudrive.com"), savedCodeCaptor.capture());
            then(publishMailEventPort).should()
                    .publishVerificationRequested(eq("river@modudrive.com"), publishedCodeCaptor.capture());

            assertThat(savedCodeCaptor.getValue()).matches("\\d{6}");
            assertThat(publishedCodeCaptor.getValue()).isEqualTo(savedCodeCaptor.getValue());
        }
    }

    @Nested
    @DisplayName("이메일이 이미 존재할 때")
    class WhenEmailIsDuplicate {

        @Test
        void throwsBusinessExceptionAndSkipsPublishing() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(true);

            Throwable thrown = catchThrowable(() -> requestEmailVerificationService.requestEmailVerification(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.DUPLICATE_EMAIL);
            then(emailVerificationTokenPort).shouldHaveNoInteractions();
            then(publishMailEventPort).shouldHaveNoInteractions();
        }
    }
}
