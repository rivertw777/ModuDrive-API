package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.springframework.beans.factory.annotation.Value;

@UseCase
class SendShareInviteMailService implements SendShareInviteMailUseCase {

    private final SendMailPort sendMailPort;
    private final String clientUrl;

    SendShareInviteMailService(SendMailPort sendMailPort, @Value("${client.url}") String clientUrl) {
        this.sendMailPort = sendMailPort;
        this.clientUrl = clientUrl;
    }

    @Override
    public void sendShareInviteMail(SendShareInviteMailCommand command) {
        String body = "'%s' 파일에 %s 권한으로 접근할 수 있게 되었습니다. ModuDrive에서 확인해보세요."
                .formatted(command.getFileName(), command.getRole());
        // A guest invite (no ModuDrive account for this email) has no login-gated deep link to
        // send instead, so the mail carries the file's no-login link directly. fileId keeps the
        // URL stable; key is the capability that authorizes it and survives the guest signing up.
        if (command.getLinkToken() != null) {
            body += "\n\n%s/public/%s?key=%s".formatted(clientUrl, command.getFileId(), command.getLinkToken());
        }

        sendMailPort.send(command.getEmail(), "[ModuDrive] 파일이 공유되었습니다", body);
    }
}
