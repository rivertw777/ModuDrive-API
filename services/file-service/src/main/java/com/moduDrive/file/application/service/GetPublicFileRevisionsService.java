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

/** No caller: {@link PublicFileResolver} accepting {@code fileId}/{@code key} is the
 * authorization. Every role includes DOWNLOAD. */
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
