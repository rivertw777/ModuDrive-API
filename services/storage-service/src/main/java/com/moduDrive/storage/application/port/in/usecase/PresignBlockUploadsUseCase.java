package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand;

import java.util.List;

public interface PresignBlockUploadsUseCase {

    /** One presigned PUT URL per block, in order. */
    List<String> presignBlockUploads(PresignBlockUploadsCommand command);
}
