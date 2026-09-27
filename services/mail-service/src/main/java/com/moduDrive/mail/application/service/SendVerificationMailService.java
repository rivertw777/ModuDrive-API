package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand.Purpose;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@UseCase
@RequiredArgsConstructor
class SendVerificationMailService implements SendVerificationMailUseCase {

    private final SendMailPort sendMailPort;
    private final String signUpTemplate = MailTemplates.load("/templates/verification-mail.html");
    private final String loginTemplate = MailTemplates.load("/templates/login-verification-mail.html");

    @Override
    public void sendVerificationMail(SendVerificationMailCommand command) {
        boolean login = command.getPurpose() == Purpose.LOGIN;
        String html = (login ? loginTemplate : signUpTemplate)
                .replace("{{CODE}}", HtmlUtils.htmlEscape(command.getVerificationCode()));
        String subject = login ? "[ModuDrive] 새 기기 로그인 인증 코드" : "[ModuDrive] 이메일 인증을 완료해주세요";

        // A display name, so the inbox shows "ModuDrive" rather than the bare sending address.
        sendMailPort.sendHtml(command.getEmail(), subject, html, "ModuDrive",
                Map.of("logo", MailTemplates.LOGO_PNG, "warning", MailTemplates.WARNING_PNG));
    }
}
