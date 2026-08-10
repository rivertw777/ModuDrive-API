package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.SaveRefreshTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.val;

@UseCase
@RequiredArgsConstructor
class LoginService implements LoginUseCase {

    private final AuthenticateMemberPort authenticateMemberPort;
    private final GenerateTokenPort generateTokenPort;
    private final SaveRefreshTokenPort saveRefreshTokenPort;

    @Override
    public TokenPair login(LoginCommand loginCommand) {
        val request = new AuthenticateMemberRequest(
                loginCommand.getMemberEmail().value(),
                loginCommand.getMemberPassword().value()
        );
        MemberAuthData memberAuthData = authenticateMemberPort.authenticateMember(request);

        TokenPair tokenPair = generateTokenPort.generateToken(memberAuthData);
        saveRefreshTokenPort.save(
                new TokenFamilyId(tokenPair.getFamilyId()),
                new TokenJti(tokenPair.getJti())
        );

        return tokenPair;
    }

}
