package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;

public interface SaveFilePort {

    File saveFile(File file);

    void deleteFile(FileId fileId);
}
