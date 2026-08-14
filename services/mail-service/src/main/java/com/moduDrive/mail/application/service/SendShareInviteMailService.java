package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class SendShareInviteMailService implements SendShareInviteMailUseCase {

    private final SendMailPort sendMailPort;

    @Override
    public void sendShareInviteMail(SendShareInviteMailCommand command) {
        String body = "'%s' 파일에 %s 권한으로 접근할 수 있게 되었습니다. ModuDrive에서 확인해보세요."
                .formatted(command.getFileName(), command.getRole());

        sendMailPort.send(command.getEmail(), "[ModuDrive] 파일이 공유되었습니다", body);
    }
}
