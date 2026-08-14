package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.springframework.beans.factory.annotation.Value;

@UseCase
class SendVerificationMailService implements SendVerificationMailUseCase {

    private final SendMailPort sendMailPort;
    private final String verifyBaseUrl;

    SendVerificationMailService(SendMailPort sendMailPort,
                                @Value("${modudrive.mail.verify-base-url}") String verifyBaseUrl) {
        this.sendMailPort = sendMailPort;
        this.verifyBaseUrl = verifyBaseUrl;
    }

    @Override
    public void sendVerificationMail(SendVerificationMailCommand command) {
        String link = verifyBaseUrl + "/api/v1/member/verify-email?token=" + command.getVerificationToken();
        String body = "ModuDrive 회원가입을 계속하려면 아래 링크를 클릭해 이메일을 인증해주세요.\n\n%s"
                .formatted(link);

        sendMailPort.send(command.getEmail(), "[ModuDrive] 이메일 인증을 완료해주세요", body);
    }
}
