package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ListPublicDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListPublicDirectoryUseCase;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class ListPublicDirectoryService implements ListPublicDirectoryUseCase {

    private final PublicFileResolver publicFileResolver;

    @Transactional(readOnly = true)
    @Override
    public List<File> listPublicDirectory(ListPublicDirectoryCommand command) {
        return publicFileResolver.resolveChildren(command.getFileId(), command.getKey());
    }
}
