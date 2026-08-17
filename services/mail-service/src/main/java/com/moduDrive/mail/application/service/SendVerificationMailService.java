package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class SendVerificationMailService implements SendVerificationMailUseCase {

    private final SendMailPort sendMailPort;

    @Override
    public void sendVerificationMail(SendVerificationMailCommand command) {
        String body = "ModuDrive 회원가입을 위한 인증 코드입니다.\n\n인증 코드: %s\n\n본인이 요청하지 않았다면 이 메일을 무시하세요."
                .formatted(command.getVerificationCode());

        sendMailPort.send(command.getEmail(), "[ModuDrive] 이메일 인증을 완료해주세요", body);
    }
}
