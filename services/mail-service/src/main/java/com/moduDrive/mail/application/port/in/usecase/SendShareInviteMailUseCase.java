package com.moduDrive.mail.application.port.in.usecase;

import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;

public interface SendShareInviteMailUseCase {
    void sendShareInviteMail(SendShareInviteMailCommand command);
}
