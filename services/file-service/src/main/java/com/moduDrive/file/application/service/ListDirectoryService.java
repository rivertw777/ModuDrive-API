package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListDirectoryUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class ListDirectoryService implements ListDirectoryUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;

    @Transactional(readOnly = true)
    @Override
    public List<File> listDirectory(ListDirectoryCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        return findFilePort.findByNamespaceIdAndPath(new NamespaceId(namespace.getId()), command.getPath().value());
    }
}
