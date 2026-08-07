package com.moduDrive.member.adapter.out.security;

import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.MatchesPasswordPort;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SpringPasswordEncoder implements EncodePasswordPort, MatchesPasswordPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public MemberPassword encodePassword(MemberPassword memberPassword) {
        return new MemberPassword(
                passwordEncoder.encode(memberPassword.passwordValue())
        );
    }

    @Override
    public boolean matchesPassword(MemberPassword rawPassword, MemberPassword encodedPassword) {
        return passwordEncoder.matches(
                rawPassword.passwordValue(),
                encodedPassword.passwordValue()
        );
    }

}
