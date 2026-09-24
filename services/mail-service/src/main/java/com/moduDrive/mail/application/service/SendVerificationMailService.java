package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@UseCase
@RequiredArgsConstructor
class SendVerificationMailService implements SendVerificationMailUseCase {

    private final SendMailPort sendMailPort;
    private final String template = MailTemplates.load("/templates/verification-mail.html");

    @Override
    public void sendVerificationMail(SendVerificationMailCommand command) {
        String html = template.replace("{{CODE}}", HtmlUtils.htmlEscape(command.getVerificationCode()));

        // A display name, so the inbox shows "ModuDrive" rather than the bare sending address.
        sendMailPort.sendHtml(command.getEmail(), "[ModuDrive] 이메일 인증을 완료해주세요", html, "ModuDrive",
                Map.of("logo", MailTemplates.LOGO_PNG, "warning", MailTemplates.WARNING_PNG));
    }
}
