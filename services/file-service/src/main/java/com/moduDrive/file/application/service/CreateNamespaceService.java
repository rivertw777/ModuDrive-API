package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.in.usecase.CreateNamespaceUseCase;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveNamespacePort;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class CreateNamespaceService implements CreateNamespaceUseCase {

    private final FindNamespacePort findNamespacePort;
    private final SaveNamespacePort saveNamespacePort;

    @Transactional
    @Override
    public Namespace createNamespace(CreateNamespaceCommand command) {
        if (findNamespacePort.existsByUserId(command.getUserId())) {
            throw new BusinessException(FileExceptionCase.NAMESPACE_ALREADY_EXISTS);
        }
        Namespace namespace = Namespace.create(command.getUserId());
        return saveNamespacePort.saveNamespace(namespace);
    }
}
