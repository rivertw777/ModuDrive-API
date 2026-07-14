package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.domain.model.File;

public interface UploadFileMetadataUseCase {

    File uploadFileMetadata(UploadFileMetadataCommand command);
}
