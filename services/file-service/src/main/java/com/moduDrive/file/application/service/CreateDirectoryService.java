package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.CreateDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.CreateDirectoryUseCase;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class CreateDirectoryService implements CreateDirectoryUseCase {

    private final FindNamespacePort findNamespacePort;
    private final SaveFilePort saveFilePort;

    @Transactional
    @Override
    public File createDirectory(CreateDirectoryCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(new NamespaceUserId(command.getUserId()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        File directory = File.create(
                new FileNamespaceId(namespace.getId()),
                command.getName(),
                command.getPath(),
                command.getOwnerId(),
                new FileIsDirectory(true)
        );
        return saveFilePort.saveFile(directory);
    }
}
