package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileRevisionsUseCase;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The link token is the whole credential, so there is no caller id and no FileAccessGuard check —
 * {@link PublicFileResolver} deciding the token is live is the authorization. Download is the only
 * thing this enables, and every link role includes it. */
@UseCase
@RequiredArgsConstructor
class GetPublicFileRevisionsService implements GetPublicFileRevisionsUseCase {

    private final PublicFileResolver publicFileResolver;
    private final FindFileVersionsPort findFileVersionsPort;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getPublicFileRevisions(GetPublicFileRevisionsCommand command) {
        File file = publicFileResolver.resolve(command.getFileId(), command.getKey());
        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(new FileId(file.getId()), command.getLimit());
    }
}
