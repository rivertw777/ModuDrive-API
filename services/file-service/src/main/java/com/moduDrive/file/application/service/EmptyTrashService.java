package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;
import com.moduDrive.file.application.port.in.usecase.EmptyTrashUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
class EmptyTrashService implements EmptyTrashUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FilePurger filePurger;

    // Not @Transactional: FilePurger.purgeRoot runs each root in its own transaction, so one
    // root's failure doesn't roll back the others.
    @Override
    public void emptyTrash(EmptyTrashCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        List<File> deleted = findFilePort.findTrashedNotPurged(namespaceId);

        // Only the roots: purging a trashed directory already cascades onto its descendants.
        Set<String> deletedDirectoryFullPaths = deleted.stream()
                .filter(File::isDirectory)
                .map(File::fullPath)
                .collect(Collectors.toSet());

        UUID callerId = command.getUserId().value();
        deleted.stream()
                .filter(file -> !deletedDirectoryFullPaths.contains(file.getPath()))
                .forEach(root -> purgeRootSkippingFailures(root, callerId));
    }

    private void purgeRootSkippingFailures(File root, UUID deletedBy) {
        try {
            filePurger.purgeRoot(root, deletedBy);
        } catch (RuntimeException e) {
            log.error("Failed to purge trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
        }
    }
}
