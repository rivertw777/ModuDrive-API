package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;

public interface SaveFilePort {

    File saveFile(File file);
}
