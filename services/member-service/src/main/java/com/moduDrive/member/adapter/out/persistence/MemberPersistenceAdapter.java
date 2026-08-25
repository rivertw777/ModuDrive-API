package com.moduDrive.member.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.SignUpMemberPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

@RequiredArgsConstructor
@PersistenceAdapter
class MemberPersistenceAdapter implements
        SignUpMemberPort, FindMemberPort, CheckEmailExistsPort {

    private final SpringDataMemberRepository springDataMemberRepository;
    private final MemberMapper memberMapper;

    @Override
    public Member createMember(Member member) {
        MemberJpaEntity entity = new MemberJpaEntity(
                member.getName(),
                member.getEmail(),
                member.getPassword(),
                member.getRoles(),
                member.isValid()
        );

        // existsByEmail in the service layer isn't atomic with this insert, so a concurrent
        // sign-up for the same email can still slip past it — saveAndFlush forces the unique
        // constraint violation to surface here (a plain save() only defers to a later,
        // uncontrolled flush) instead of as a raw 500 at commit time.
        MemberJpaEntity saved;
        try {
            saved = springDataMemberRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (isEmailConflict(e)) {
                throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
            }
            throw e;
        }
        return memberMapper.mapToDomainEntity(saved);
    }

    // Only the email uniqueness violation should be reported as "duplicate email" — this insert
    // also flushes the member_role collection, so a NOT NULL/FK/other integrity violation there
    // is a real bug and should surface as-is rather than being misreported. Same pattern as
    // FilePersistenceAdapter.isActiveSlotConflict.
    private static boolean isEmailConflict(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getMessage() != null
                && cause.getMessage().toLowerCase().contains("uk_member_email");
    }

    @Override
    public boolean existsByEmail(MemberEmail memberEmail) {
        return springDataMemberRepository.existsByEmail(memberEmail.emailValue());
    }

    @Override
    public Member findMemberByEmail(MemberEmail memberEmail) {
        MemberJpaEntity entity = springDataMemberRepository.findByEmail(memberEmail.emailValue())
                .orElseThrow(() -> new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND));

        return memberMapper.mapToDomainEntity(entity);
    }

    @Override
    public Member findMemberById(MemberId memberId) {
        MemberJpaEntity entity = springDataMemberRepository.findById(memberId.idValue())
                .orElseThrow(() -> new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND));

        return memberMapper.mapToDomainEntity(entity);
    }

}
