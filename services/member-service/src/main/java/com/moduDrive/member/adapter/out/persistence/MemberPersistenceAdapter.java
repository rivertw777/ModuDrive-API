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

        MemberJpaEntity saved = springDataMemberRepository.save(entity);
        return memberMapper.mapToDomainEntity(saved);
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
