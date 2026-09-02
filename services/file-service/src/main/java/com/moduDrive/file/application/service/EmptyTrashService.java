package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;
import com.moduDrive.file.application.port.in.usecase.EmptyTrashUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
class EmptyTrashService implements EmptyTrashUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FilePurger filePurger;

    // Not @Transactional here on purpose: FilePurger.purgeRoot opens its own transaction per
    // root (see its javadoc), so one root's failure can't roll back roots already purged in this
    // same call — wrapping the whole loop in one transaction would undo that isolation.
    @Override
    public void emptyTrash(EmptyTrashCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        NamespaceId namespaceId = new NamespaceId(namespace.getId());
        List<File> deleted = findFilePort.findByNamespaceIdAndStatus(namespaceId, FileStatus.DELETED);

        // Same trick as ListTrashService: purging a trashed directory root cascades onto every
        // descendant, so purging both the root and its already-covered descendants would double
        // up work (deleteFile on an already-deleted row is harmless, but pointless).
        Set<String> deletedDirectoryFullPaths = deleted.stream()
                .filter(File::isDirectory)
                .map(File::fullPath)
                .collect(Collectors.toSet());

        deleted.stream()
                .filter(file -> !deletedDirectoryFullPaths.contains(file.getPath()))
                .forEach(this::purgeRootSkippingFailures);
    }

    private void purgeRootSkippingFailures(File root) {
        try {
            filePurger.purgeRoot(root);
        } catch (RuntimeException e) {
            // One bad root (storage-service unreachable, a since-changed row) must not abort
            // every other root already queued in this empty-trash call.
            log.error("Failed to purge trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
        }
    }
}
