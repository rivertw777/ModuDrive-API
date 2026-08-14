package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.SendShareInviteMailCommand;

public interface SendShareInviteMailUseCase {
    void sendShareInviteMail(SendShareInviteMailCommand command);
}
