package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.IssueStreamTokenCommand;

public interface IssueStreamTokenUseCase {

    String issue(IssueStreamTokenCommand command);
}
