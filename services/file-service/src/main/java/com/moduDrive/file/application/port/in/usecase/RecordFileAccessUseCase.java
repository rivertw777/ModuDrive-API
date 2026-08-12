package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;

public interface RecordFileAccessUseCase {

    void recordAccess(RecordFileAccessCommand command);
}
