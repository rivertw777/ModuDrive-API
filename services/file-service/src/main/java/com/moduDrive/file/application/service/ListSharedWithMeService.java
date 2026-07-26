package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.in.usecase.ListSharedWithMeUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
class ListSharedWithMeService implements ListSharedWithMeUseCase {

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;

    // ponytail: one findById per share (N+1); fine at this scale, batch-fetch
    // by file id if a user's share count grows large enough to matter.
    @Transactional(readOnly = true)
    @Override
    public List<File> listSharedWithMe(ListSharedWithMeCommand command) {
        return findFileSharePort.findBySharedWithUserId(command.getSharedWithUserId())
                .stream()
                .map(share -> findFilePort.findById(new FileId(share.getFileId())))
                .flatMap(Optional::stream)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .toList();
    }
}
