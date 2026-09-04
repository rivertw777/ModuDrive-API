package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileScopeUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class UpdateFileScopeService implements UpdateFileScopeUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FindFileSharePort findFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final SaveFileSharePort saveFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File updateFileScope(UpdateFileScopeCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        if (command.getScope() == ShareScope.LINK) {
            // A link is a bearer credential anyone who obtains it can use, unlike a named
            // RESTRICTED grant — so it may only ever hand out read-only access.
            if (command.getRole() != Role.VIEWER) {
                throw new BusinessException(FileExceptionCase.INVALID_LINK_ROLE);
            }
            // File.enableLinkSharing keeps an existing token, so re-selecting LINK doesn't
            // silently break links already shared.
            file.enableLinkSharing(UUID.randomUUID(), command.getRole());
        } else {
            file.disableLinkSharing();
            // Turning sharing off must kill every outstanding anonymous capability, not just the
            // file's own link token — otherwise a guest invite mailed earlier keeps working forever.
            revokeGuestCapabilities(command.getFileId());
        }

        return saveFilePort.saveFile(file);
    }

    private void revokeGuestCapabilities(File.FileId fileId) {
        for (FileShare share : findFileSharePort.findByFileId(fileId)) {
            if (share.getSharedWithUserId() == null) {
                // An invite nobody has claimed — no member behind it, so the whole row goes.
                deleteFileSharePort.deleteFileShare(new FileShareId(share.getId()));
            } else if (share.getToken() != null) {
                // A claimed guest share: keep the member grant, drop only the anonymous bearer
                // link so it dies with the scope change like the file's own link token does.
                share.revokeToken();
                saveFileSharePort.saveFileShare(share);
            }
        }
    }
}
