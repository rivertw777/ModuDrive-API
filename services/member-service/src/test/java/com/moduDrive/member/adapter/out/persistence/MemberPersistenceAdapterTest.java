package com.moduDrive.member.adapter.out.persistence;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberIsValid;
import com.moduDrive.member.domain.model.Member.MemberName;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.domain.model.Member.MemberRoles;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
@Import({MemberPersistenceAdapter.class, MemberMapper.class})
class MemberPersistenceAdapterTest {

    @Autowired
    private MemberPersistenceAdapter memberPersistenceAdapter;
    @Autowired
    private SpringDataMemberRepository springDataMemberRepository;

    private final MemberName memberName = MemberTestFixture.DEFAULT_NAME;
    private final MemberEmail memberEmail = MemberTestFixture.DEFAULT_EMAIL;
    private final MemberPassword memberPassword = MemberTestFixture.DEFAULT_PASSWORD;
    private final MemberRoles memberRoles = MemberTestFixture.DEFAULT_ROLES;
    private final MemberIsValid memberIsValid = MemberTestFixture.DEFAULT_IS_VALID;

    @Nested
    @DisplayName("회원을 저장할 때")
    class WhenCreatingMember {

        @Test
        void persistsMemberAndCanBeFoundByEmail() {
            Member member = MemberTestFixture.aMember();

            memberPersistenceAdapter.createMember(member);

            assertThat(memberPersistenceAdapter.existsByEmail(memberEmail)).isTrue();
        }
    }

    @Nested
    @DisplayName("이메일로 회원을 조회할 때")
    class WhenFindingByEmail {

        @BeforeEach
        void setUp() {
            springDataMemberRepository.save(
                    new MemberJpaEntity(memberName.nameValue(), memberEmail.emailValue(),
                            memberPassword.passwordValue(), memberRoles.roleValues(), memberIsValid.isValidValue()));
        }

        @Test
        void returnsMappedMemberWhenFound() {
            Member member = memberPersistenceAdapter.findMemberByEmail(memberEmail);

            assertThat(member.getEmail()).isEqualTo(memberEmail.emailValue());
            assertThat(member.getName()).isEqualTo(memberName.nameValue());
            assertThat(member.getRoles()).containsExactlyElementsOf(memberRoles.roleValues());
        }

        @Test
        void throwsBusinessExceptionWhenNotFound() {
            Throwable thrown = catchThrowable(
                    () -> memberPersistenceAdapter.findMemberByEmail(new MemberEmail("unknown@modudrive.com")));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("ID로 회원을 조회할 때")
    class WhenFindingById {

        @Test
        void returnsMappedMemberWhenFound() {
            MemberJpaEntity saved = springDataMemberRepository.save(
                    new MemberJpaEntity(memberName.nameValue(), memberEmail.emailValue(),
                            memberPassword.passwordValue(), memberRoles.roleValues(), memberIsValid.isValidValue()));

            Member member = memberPersistenceAdapter.findMemberById(new MemberId(saved.getId()));

            assertThat(member.getId()).isEqualTo(saved.getId());
            assertThat(member.getEmail()).isEqualTo(memberEmail.emailValue());
        }

        @Test
        void throwsBusinessExceptionWhenNotFound() {
            Throwable thrown = catchThrowable(
                    () -> memberPersistenceAdapter.findMemberById(new MemberId(UUID.randomUUID())));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("이메일 인증을 완료 처리할 때")
    class WhenMarkingEmailVerified {

        @Test
        void flipsIsValidToTrue() {
            MemberJpaEntity saved = springDataMemberRepository.save(
                    new MemberJpaEntity(memberName.nameValue(), memberEmail.emailValue(),
                            memberPassword.passwordValue(), memberRoles.roleValues(), false));

            memberPersistenceAdapter.markEmailVerified(new MemberId(saved.getId()));

            Member member = memberPersistenceAdapter.findMemberById(new MemberId(saved.getId()));
            assertThat(member.isValid()).isTrue();
        }

        @Test
        void throwsBusinessExceptionWhenNotFound() {
            Throwable thrown = catchThrowable(
                    () -> memberPersistenceAdapter.markEmailVerified(new MemberId(UUID.randomUUID())));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.MEMBER_NOT_FOUND);
        }
    }
}
