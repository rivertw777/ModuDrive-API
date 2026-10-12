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

        file.markFavorite(fileFavoritePort.isFavorite(command.getCallerId(), file.getId()));
        if (file.getOwnerId().equals(command.getCallerId())) {
            return FileView.owned(file);
        }

        // The grant behind the access, direct or inherited, gives "공유한 사용자/날짜". No grant
        // means access through a LINK scope: the owner's identity is not shown to a link-only
        // visitor.
        Optional<FileShare> grant = Optional.ofNullable(access.grant());
        MemberSummary sharedBy = grant.isPresent()
                ? findMemberByIdPort.findMemberByIdOrUnknown(file.getOwnerId())
                : FindMemberByIdPort.UNKNOWN_MEMBER;
        LocalDateTime sharedAt = grant.map(FileShare::getCreatedAt).orElse(null);
        return new FileView(file, access.role(),
                sharedBy.name(), sharedBy.email(), sharedAt, null, null);
    }
}
