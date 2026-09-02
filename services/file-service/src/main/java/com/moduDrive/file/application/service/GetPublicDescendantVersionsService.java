package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.GetPublicDescendantVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicDescendantVersionsUseCase;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The folder link token is the whole credential; {@link PublicFileResolver#resolveDescendant}
 * confirming the entry really is under that link-shared folder is the authorization. Download is
 * the only thing this enables, which every link role includes. */
@UseCase
@RequiredArgsConstructor
class GetPublicDescendantVersionsService implements GetPublicDescendantVersionsUseCase {

    private final PublicFileResolver publicFileResolver;
    private final FindFileVersionsPort findFileVersionsPort;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getPublicDescendantVersions(GetPublicDescendantVersionsCommand command) {
        File file = publicFileResolver.resolveDescendant(command.getToken(), command.getEntryId());
        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(new FileId(file.getId()), command.getLimit());
    }
}
