package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListTrashCommand;
import com.moduDrive.file.application.port.in.usecase.ListTrashUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
class ListTrashService implements ListTrashUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FavoriteEnricher favoriteEnricher;

    @Transactional(readOnly = true)
    @Override
    public List<File> listTrash(ListTrashCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        List<File> deleted = favoriteEnricher.withFavorites(command.getUserId().value(),
                findFilePort.findTrashedNotPurged(new NamespaceId(namespace.getId())));

        // Deleting a directory cascades DELETED onto every descendant, so trash would otherwise
        // list each nested file/folder too — keep only the roots of each deleted subtree.
        Set<String> deletedDirectoryFullPaths = deleted.stream()
                .filter(File::isDirectory)
                .map(File::fullPath)
                .collect(Collectors.toSet());

        return deleted.stream()
                .filter(file -> !deletedDirectoryFullPaths.contains(file.getPath()))
                .collect(Collectors.toList());
    }
}
