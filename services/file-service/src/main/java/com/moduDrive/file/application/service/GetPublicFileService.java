package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileUseCase;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class GetPublicFileService implements GetPublicFileUseCase {

    private final PublicFileResolver publicFileResolver;

    @Transactional(readOnly = true)
    @Override
    public File getPublicFile(GetPublicFileCommand command) {
        return publicFileResolver.resolve(command.getToken());
    }
}
