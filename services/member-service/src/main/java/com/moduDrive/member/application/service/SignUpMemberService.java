package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.event.MemberSignedUpEvent;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.in.usecase.SignUpMemberUseCase;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.SignUpMemberPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberIsValid;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.domain.model.Member.MemberRoles;
import com.moduDrive.member.domain.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class SignUpMemberService implements SignUpMemberUseCase {

    private final SignUpMemberPort signUpMemberPort;
    private final EncodePasswordPort encodePasswordPort;
    private final CheckEmailExistsPort checkEmailExistsPort;
    private final EmailVerificationTokenPort emailVerificationTokenPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void signUpMember(SignUpMemberCommand signUpMemberCommand) {
        validateEmailNotDuplicated(signUpMemberCommand.getMemberEmail());
        validateEmailVerified(signUpMemberCommand.getMemberEmail());
        MemberPassword encodedPassword = encodePasswordPort.encodePassword(signUpMemberCommand.getMemberPassword());

        // Email is already confirmed via the pre-signup verify-email flow, so the member starts valid.
        Member member = Member.create(
                signUpMemberCommand.getMemberName(),
                signUpMemberCommand.getMemberEmail(),
                encodedPassword,
                new MemberRoles(List.of(Role.MEMBER)),
                new MemberIsValid(true)
        );
        Member savedMember = signUpMemberPort.createMember(member);
        // The Feign call to file-service and the Kafka publish used to run right here, inside this
        // @Transactional — holding the DB connection for an HTTP round trip, and (on a later commit
        // failure) leaving an already-published MemberSignedUp for a member that was never actually
        // created. MemberSignedUpEventListener runs both AFTER_COMMIT instead (#208).
        eventPublisher.publishEvent(new MemberSignedUpEvent(savedMember.getId(), savedMember.getEmail()));
    }

    private void validateEmailNotDuplicated(MemberEmail memberEmail) {
        if (checkEmailExistsPort.existsByEmail(memberEmail)) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }
    }

    private void validateEmailVerified(MemberEmail memberEmail) {
        if (!emailVerificationTokenPort.consumeVerified(memberEmail.emailValue())) {
            throw new BusinessException(MemberExceptionCase.EMAIL_NOT_VERIFIED);
        }
    }

}
