package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileVersion;

public interface SaveFileVersionPort {

    FileVersion saveFileVersion(FileVersion fileVersion);
}
