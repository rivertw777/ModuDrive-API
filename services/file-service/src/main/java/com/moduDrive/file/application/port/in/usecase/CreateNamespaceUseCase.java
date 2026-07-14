package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.domain.model.Namespace;

public interface CreateNamespaceUseCase {

    Namespace createNamespace(CreateNamespaceCommand command);
}
