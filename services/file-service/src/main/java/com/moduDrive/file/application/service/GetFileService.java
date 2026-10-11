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
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
class GetFileService implements GetFileUseCase {

    private final FindFilePort findFilePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public FileView getFile(GetFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        FileAccessGuard.Access access = fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.READ);

        if (file.isRemoved()) {
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
        // from an ancestor (see ListSharedDirectoryService) — the guard's access() covers both so
        // "공유된 날짜" isn't blank just because this particular file was never shared directly.
        //
        // No grant at all means the caller only got here through the LINK fallback in
        // FileAccessGuard.access — nobody shared this file *with them*, so there is no
        // "공유한 사용자" to name. Spec 3 gives a link-only visitor viewer access, not the owner's
        // identity: a link forwarded to a stranger must not leak whose drive it came from (#319).
        // The permission check above already resolved both; reused rather than walked again.
        Optional<FileShare> grant = Optional.ofNullable(access.grant());
        MemberSummary sharedBy = grant.isPresent()
                ? findMemberByIdPort.findMemberByIdOrUnknown(file.getOwnerId())
                : FindMemberByIdPort.UNKNOWN_MEMBER;
        LocalDateTime sharedAt = grant.map(FileShare::getCreatedAt).orElse(null);
        return new FileView(file, access.role(),
                sharedBy.name(), sharedBy.email(), sharedAt, null, null);
    }
}
