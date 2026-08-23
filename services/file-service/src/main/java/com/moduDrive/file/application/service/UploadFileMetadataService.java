package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.application.port.in.usecase.UploadFileMetadataUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UploadFileMetadataService implements UploadFileMetadataUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File uploadFileMetadata(UploadFileMetadataCommand command) {
        Namespace namespace = findNamespacePort.findByUserId(new NamespaceUserId(command.getUserId()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        // Only an *active* file occupies the name/path slot (a trashed one doesn't — see
        // uk_file_namespace_path_active_name), so this never sees, and never touches, whatever's
        // sitting in trash under the same name: that upload always falls through to a fresh,
        // independent file below.
        var existingActive = findFilePort.findActiveByNamespaceIdAndPathAndName(
                new NamespaceId(namespace.getId()), command.getPath().value(), command.getName().value());
        if (existingActive.isPresent()) {
            File file = existingActive.get();
            // A type clash (file vs. directory) is never a replace target; same type without
            // explicit consent means the client hasn't asked to replace yet (Drive-style "이미
            // 존재합니다 — 대체/둘 다 유지" prompt belongs on the client, this is what it checks).
            if (!command.isReplaceExisting() || file.isDirectory() != command.getIsDirectory().value()) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            // Reaching here already implies ownership (existingActive only ever searches the
            // caller's own namespace, and every file in a namespace is owned by that namespace's
            // user) — this guard doesn't change that, it just stops this being the one
            // file-mutating use case that leaves it implicit instead of checked, the way
            // Rename/Move/Restore/UpdateFileStatus all do via the same guard.
            fileAccessGuard.requireOwner(file, command.getUserId());
            file.restartUpload();
            return saveFilePort.saveFile(file);
        }

        File file = File.create(
                new FileNamespaceId(namespace.getId()),
                command.getName(),
                command.getPath(),
                command.getOwnerId(),
                command.getIsDirectory()
        );
        return saveFilePort.saveFile(file);
    }
}
