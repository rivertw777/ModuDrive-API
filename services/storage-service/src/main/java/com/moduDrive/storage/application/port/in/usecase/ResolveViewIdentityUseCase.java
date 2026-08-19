package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.ResolveViewIdentityCommand;

import java.util.UUID;

public interface ResolveViewIdentityUseCase {

    UUID resolve(ResolveViewIdentityCommand command);
}
