package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.application.port.in.usecase.ListRecentFilesUseCase;
import com.moduDrive.file.application.port.out.FindFileAccessPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListRecentFilesService implements ListRecentFilesUseCase {

    private final FindFileAccessPort findFileAccessPort;
    private final FindFilePort findFilePort;
    private final FindNamespacePort findNamespacePort;
    private final FindFileSharePort findFileSharePort;

    // ponytail: one findById per access row (N+1), same tradeoff as ListSharedWithMeService;
    // fine at this scale, batch-fetch by file id if a user's recent list grows large.
    @Transactional(readOnly = true)
    @Override
    public List<File> listRecentFiles(ListRecentFilesCommand command) {
        UUID userId = command.getUserId().value();
        UUID ownNamespaceId = findNamespacePort.findByUserId(new NamespaceUserId(userId))
                .map(Namespace::getId)
                .orElse(null);

        return findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, command.getLimit())
                .stream()
                .map(access -> findFilePort.findById(new FileId(access.getFileId())))
                .flatMap(Optional::stream)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                // A file recorded as "recently opened" may since have had its share revoked
                // or moved out of reach — recent must reflect what the viewer can access
                // *now*, not just what they once opened, so re-check ownership/share here
                // rather than trusting the access log alone (see ListSharedWithMeService,
                // which sources from live share rows for the same reason).
                .filter(file -> file.getNamespaceId().equals(ownNamespaceId)
                        || findFileSharePort.existsByFileIdAndSharedWithUserId(new FileId(file.getId()), userId))
                .toList();
    }
}
