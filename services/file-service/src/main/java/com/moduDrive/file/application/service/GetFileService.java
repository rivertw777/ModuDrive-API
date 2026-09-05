package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.GetFileUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@UseCase
@RequiredArgsConstructor
class GetFileService implements GetFileUseCase {

    private static final MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    private final FindFilePort findFilePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public FileView getFile(GetFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.READ);

        if (file.getStatus() == FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_ALREADY_DELETED);
        }

        // Per-viewer star (owner's or a grantee's — same file_favorite lookup either way).
        file.markFavorite(fileFavoritePort.isFavorite(command.getCallerId(), file.getId()));
        if (file.getOwnerId().equals(command.getCallerId())) {
            return FileView.owned(file);
        }

        // The caller doesn't own it: the detail panel shows the full "shared with me" context —
        // same as 공유 문서함 — wherever it opens. Their access can come from a grant on this file
        // itself or, for a file only reachable by browsing into a shared folder, one inherited
        // from an ancestor (see ListSharedDirectoryService) — resolveGrant covers both so
        // "공유된 날짜" isn't blank just because this particular file was never shared directly.
        MemberSummary sharedBy = lookupMember(file.getOwnerId());
        LocalDateTime sharedAt = fileAccessGuard.resolveGrant(file, command.getCallerId())
                .map(FileShare::getCreatedAt)
                .orElse(null);
        return new FileView(file, fileAccessGuard.effectiveRole(file, command.getCallerId()),
                sharedBy.name(), sharedBy.email(), sharedAt, null, null);
    }

    private MemberSummary lookupMember(UUID memberId) {
        try {
            return findMemberByIdPort.findMemberById(memberId);
        } catch (RuntimeException e) {
            log.warn("Failed to resolve sharer {} for file detail, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
    }
}
